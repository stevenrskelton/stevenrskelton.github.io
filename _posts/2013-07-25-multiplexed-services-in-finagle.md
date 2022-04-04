---
#layout: post
title: "Multiplexed Services in Finagle"
categories:
  - Thrift
  - Scala
---

[Apache Thrift](http://thrift.apache.org/) is a pretty good [RPC library](http://en.wikipedia.org/wiki/Remote_procedure_call).  Methods compose a service, and the service is hosted on a raw TCP port. Even a large implementation with a hundred methods will perform effortlessly, but for organizational purposes you’ll want to group calls together into separate services. The standard thrift protocols require that each service retain exclusive use to its own TCP port, creating a firewall maintenance nightmare.

Enter service multiplexing: the ability to run all services on a single port. Under the covers it prepends the service name to method calls, and it can do this transparently without effecting your code.

Protocol multiplexing hit the Thrift master branch in March 2013, and unless you are rolling your own releases there hasn’t been a [libthrift release since 0.9](http://search.maven.org/#search|ga|1|libthrift) in October.  Unsurprisingly, without a formal release dependent projects like [Twitter Finagle](http://twitter.github.io/finagle/) have had to hold off on implementation. 
_(I’ll note that since Finagle still links to libthrift-0.5, don’t hold your breath in any case)_

On the surface, multiplexing is a small change.  All that is required is a small broker class to wire up services and perform routing.  But under the covers, Thrift and Finagle are slightly different.  Thrift’s multiplexing functionality was put into the [org.apache.thrift.TProcessor](https://github.com/apache/thrift/blob/master/lib/java/src/org/apache/thrift/TMultiplexedProcessor.java), a class that doesn’t even exist in Finagle-Thrift.

Looking a the generated classes, it appears that Thrift’s non-thread safe server implementation processes a [TProtocol](https://github.com/apache/thrift/blob/master/lib/java/src/org/apache/thrift/protocol/TProtocol.java), while Finagle’s [NIO](http://en.wikipedia.org/wiki/Asynchronous_I/O) server implementation processes raw byte arrays.  So it looks like we might need to do a little byte rewriting to solve this.

In lieu of a `TProcessor`, Finagle exposes a hook to intercept the raw bytes in and out of the service in the [com.twitter.finagle.Filter](https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/Filter.scala) class.

Filters allow both the request and response to be modified before transmission over the wire. A `Filter` that maps to the identity (ie: return is identical to input) has the form:

```scala
def identity[Req, Rep] = new SimpleFilter[Req, Rep] {
  def apply(request: Req, service: Service[Req, Rep]) = service(request)
}
```

With multiplexing, many different services can use the same connection for their requests. It will be our job to expand the `Filter`’s `apply` method to route requests to the appropriate services.

If you don’t want to mangle your [Finagle](http://twitter.github.io/finagle/) POM files, don’t worry, multiplexed services can be implemented without upgrading your libthrift from 0.9. Manually include the new code into your own project:

- [org.apache.thrift.protocol.TProtocolDecorator.java](https://raw.github.com/apache/thrift/master/lib/java/src/org/apache/thrift/protocol/TProtocolDecorator.java)
- [org.apache.thrift.protocol.TMultiplexedProtocol.java](https://raw.github.com/apache/thrift/master/lib/java/src/org/apache/thrift/protocol/TMultiplexedProtocol.java)

All of `org.apache.thrift.TMultiplexedProcessor.java`s will be reimplemented to act on `Array[Byte]`s.

Ideally, we want to continue using the `ServerBuilder()` construct, and follow the same pattern used in code>`TMultiplexedProcessor` – ie: supply a map of our original services.

```scala
import com.twitter.util.Future
import org.apache.thrift.transport.TMemoryInputTransport
import org.apache.thrift.protocol.{ TBinaryProtocol, TMessage }
import org.apache.thrift.TException
import com.twitter.finagle.Service
 
class MultiplexedFinagleService
 (val serviceMap: Map[String, Service[Array[Byte], Array[Byte]]])
 extends Service[Array[Byte], Array[Byte]] {
 
 val protocolFactory = new TBinaryProtocol.Factory
 
 final def apply(request: Array[Byte]): Future[Array[Byte]] = {
  val inputTransport = new TMemoryInputTransport(request)
  val iprot = protocolFactory.getProtocol(inputTransport)
 
  try{
 
   //read in multiplexed message name
   val msg = iprot.readMessageBegin
   val (serviceName, methodName) = splitServiceNameFromMethod(msg)
 
   //find matching service
   val service = serviceMap.getOrElse(serviceName,
    throw new TException(s"Service `$serviceName` not found."))
 
   //rewrite message to original non-multiplexed format
   val mappedRequest = mapRequestToNewMethod(request, 
    msg.name, methodName)
   service.apply(mappedRequest)
 
  }catch{
   case e: Exception => Future.exception(e)
  }
 }
}
```

We make reference to two new functions, `splitServiceNameFromMethod` and `mapRequestToNewMethod`. It’s important to note the hard-coded reference to TBinaryProtocol, which is also hard-coded in [Scrooge](https://github.com/twitter/scrooge) generated Scala classes. It makes our implementation of `mapRequestToNewMethod` a little easier, as rewriting requests for a generalized `TProcotol` would be a bit more involved.

```scala
def splitServiceNameFromMethod(message: TMessage): (String, String) = {
 val index = message.name.indexOf(TMultiplexedProtocol.SEPARATOR)
 val serviceName = message.name.substring(0, index)
 val methodName = message.name.substring(index + 1)
 (serviceName, methodName)
}
```

The `TBinaryProtocol` isn’t complex, and lucky for us the method name is the second field in the transmission protocol, right after the `Int32` version number. All strings are serialized as an `Int32` indicating their length, then UTF-8 bytes – no null terminated strings or escape characters to worry about here.

```scala
def mapBinaryProtocolRequestToNewMethod (request: Array[Byte],
 originalMethodName: String, newMethodName: String): Array[Byte] = {
 
 val versionLength = 4 //first byte
 val stringLength = 4 //second byte
 val version = request.take(versionLength)
 val body = request.seq.drop(versionLength +
  stringLength + originalMethodName.size)
 
 val newMethodNameBytes = new MethodName.getBytes("UTF-8")
 val newStringLength = int32ToBytes(newMethodNameBytes.size)
 
 val response = new Array[Byte](versionLength + stringLength +
  newMethodNameBytes.size + body.size)
 
 version.copyToArray(response, 0)
 newStringLength.copyToArray(response, versionLength)
 newMthodNameBytes.copyToArray(response, versionLength + stringLength)
 body.copyToArray(response, versionLength + stringLength +
  newMethodNameBytes.size)
 
 response
}
```

Converting an Int32 into 4 bytes isn’t built into Scala, but it’s available in many libraries, however since it’s a simple implementation let’s just code it by hand.

```scala
private def int32ToBytes(i32: Int): Array[Byte] = {
 Array(
  (0xff & (i32 >> 24)).toByte,
  (0xff & (i32 >> 16)).toByte,
  (0xff & (i32 >> 8)).toByte,
  (0xff & (i32)).toByte)
}
```

At this point, we have all our missing Thrift multiplexing code in place, but require one more helpful constructor to create a new `TProtocolFactory` for the `TMultiplexedProtocol`.

```scala
def multiplexedBinaryProtocolFactory(serviceName: String)
 : TProtocolFactory = {
 
 new {} with TBinaryProtocol.Factory {
  override def getProtocol(trans: TTransport): TProtocol = {
   new TMultiplexedProtocol(super.getProtocol(trans), serviceName)
  }
 }
}
```

To put this all into context, let’s put together a sample multiplexed service.
Suppose we have defined two Thrift services, called `FooApi` and `BarApi`, with implementations `FooService` and `BarService` respectively.
Our multiplexed server construction still uses a fluent `ServerBuilder`, the only change is we wrap our 2 services into a single `MultiplexedFinagleService`.

```scala
val serviceMap = Map(
 "FooApi" -> new FooApi.FinagledService(new FooService, 
   multiplexedBinaryProtocolFactory("FooApi")),
 
 "BarApi" -> new BarApi.FinagledService(new BarService, 
   multiplexedBinaryProtocolFactory("BarApi"))
)
 
ServerBuilder()
 .codec(ThriftServerFramedCodec())
 .name("FooBar")
 .bindTo(new InetSocketAddress(port))
 .build(new MultiplexedFinagleService(serviceMap))
```

And our multiplexed client construction still uses a `ClientBuilder`.

```scala
val service = ClientBuilder()
 .codec(ThriftClientFramedCodec())
 .hosts(new InetSocketAddress(addr, port))
 .hostConnectionLimit(value)
 .build()
 
val fooClient = new FooApi.FinagledClient(service, 
  multiplexedBinaryProtocolFactory("FooApi"))
 
val barClient = new BarApi.FinagledClient(service, 
  multiplexedBinaryProtocolFactory("BarApi"))
```

The only minor issue I’ve encountered are the unavoidable breakages to any Filter directly referencing method names (ie: logging, statistics). They can continue to be attached to either the multiplexing service, or the multiplexed services, but a minor tweak is necessary to account for the multiplexing behaviour.

### Feedback

<table style="border:1px solid grey">
<tr><td>
<p><i>Matthew on September 18, 2013 at 11:59 am said:</i></p>
<p>Hi,<br/>Firstly thank you for your example and included code, there’s very little documentation that I am able to find on Multiplexing with Finagle and even less code examples, so +1 for your post.</p>
<p>I’m attempting to implement this with a very simple client/server, and I’m getting this exception from the client side when trying to make a request:<br/><code>SeqMismatchException: got 0, expected 1392947530 at com.twitter.finagle.thrift.SeqIdFilter$$anonfun$apply$1.apply(SeqIdFilter.scala:89)</code></p>
<p>Which is raised from here:<br/><a href="https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/scala/com/twitter/finagle/thrift/SeqIdFilter.scala#L89">https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/scala/com/twitter/finagle/thrift/SeqIdFilter.scala#L89</a></p>
<p>The 0 value is the same over any request. I note you mentioned there may be some problem with filters, any pointers?</p>
</td></tr>
<tr><td>
<p><i>steven on September 20, 2013 at 4:55 pm said:</i></p>
<p>That’s a very strange error, the server shouldn’t be modifying the SeqId. I would look closely into any other Filters.<br/>You can also try printing the Thrift transport to the console using {% link _posts/2013-08-03-developer-friendly-thrift-request-logging.md %}</p>
<p>I’ve commited Specs2 tests into GitHub for my code examples; you can compare your setup to mine:<br/>https://github.com/stevenrskelton/Blog</p>
</td></tr>
<tr><td>
<p><i>Matthew on September 23, 2013 at 7:12 am said:</i></p>
<p>Hi Steven,</p>
<p>I managed to solve that error by adding the two Java files that you referenced (sorry missed that on the first run!). It’s not producing any error now but doesn’t seem to be completing any requests. I think I need to have a look at the versions I’m running. The tests should be helpful and also the logging link, thanks again.</p>
</td></tr>
</table>


<table style="border:1px solid grey">
<tr><td>
<p><i>Tim on January 20, 2015 at 1:16 pm said:</i></p>
<p>Thanks for the informative post Steven. However, I’m curious to understand the relationship between your example, and Finagle’s ThriftMux. Are these complementary, or overlapping? (or does ThriftMux obviate the need for this approach?</p>
</td></tr>
<tr><td>
<p><i>steven on January 20, 2015 at 1:54 pm said:</i></p>
<p>Hi Tim, thanks for reading my blog.</p>
<p>At its core, Mux is a generic RPC multiplexing protocol. Although its primary implementation is as a Finagle subproject, Mux is not Finagle-specific. See Finagle’s documentation, or the W3C specification.</p>
<p>The difference here is MUX allows any number of generic services to be multiplexed over the same TCP connection, while using the TMultiplexedProcessor only allows the same TCP connection to multiplex multiple Thrift services. By default, Thrift was only allowing a single service per connection.</p>
</td></tr>
</table>


{%
  include downloadsources.html
  src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Multiplexed-Services-In-Finagle.scala"
%}