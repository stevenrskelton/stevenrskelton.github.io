---
#layout: post
title: "Thrift Client Side Caching to Speed Up Unit Tests"
categories:
  - Platform
  - Scala
tags:
  - Thrift
---

One of the largest headaches associated with network system architecture is abstracting away the network. External resources are always slower and more disjoint than working locally. While there are various caching techniques, few are suitable for use in a development environment.

Client-side unit tests usually only have two options: executing calls against a deployed server thereby struggling against long waits per testing iteration, or having all calls tediously mocked out.

An alternative approach available within Finagle: a pre-populated query cache on the client side. In my article [Finagle Query Cache with Guava](http://stevenskelton.ca/finagle-query-cache-with-guava/) the idea of using a Filter as a means of intercepting and short circuiting service calls was demonstrated. Instead of filling the cache at runtime, the known service calls can be loaded into a static map at compile.

Capturing the necessary unit test request/response pairs is quite simple using a [Finagle LoggingFilter](https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/filter/LoggingFilter.scala), but it’s hard to handle the binary output. A cleaner, and more developer friendly approach is to translate the binary data into JSON, an approach broached in {% link _posts/2013-08-03-developer-friendly-thrift-request-logging.md %}.

The advantage of re-encoding to `TJSONProtocol` is that mocked data can be contained directly within Scala files as opposed to external resources. Another advantage is the ability to log human readable output directly to the console.

Using `ProtocolHelpers` class previously constructed in {% link _posts/2013-08-03-developer-friendly-thrift-request-logging.md %} we can execute our `TProtocol↔TJSONProtocol` reserialization:

```scala
import com.twitter.finagle.{ Service, SimpleFilter }
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.util.Future
import org.apache.thrift.transport._
import org.apache.thrift.protocol._
import ProtocolHelpers.reserialize
 
/**
 *  Returns response from supplied TJSONProtocol log file,
 *   matching on serialization of request (excluding SeqId).
 */
class MockJSONDataFilter(
  finagleServiceObject: AnyRef,
  jsonLog: Seq[(String, String)],
  thriftEncoding: TProtocolFactory = new TBinaryProtocol.Factory)
  extends SimpleFilter[ThriftClientRequest, Array[Byte]] {
 
  val logEncoding = new TJSONProtocol.Factory
 
  /** All request/response pairs */
  lazy val requestResponses: Map[String, Array[Byte]]
 
  /**  Change SeqId within any TProtocol */
  def changeSeqId(requestOrResponse: Array[Byte], protocolFactory: TProtocolFactory, 
    seqId: Int = 0): (Int, Array[Byte])
 
  def apply(request: ThriftClientRequest, service: Service[ThriftClientRequest, Array[Byte]])
    : Future[Array[Byte]] 
}
```

The `thriftEncoding` is what is transmitted over the wire, it can be any `TProtocol`. The `jsonLog` is assumed to use the `TJSONProtocol` since it is the only full-featured human readable `String TProtocol` in Thrift.

The approach taken is to populate the requestResponses cache with the supplied `jsonLog`, and throw an exception if any requests are made that aren’t in this cache (this will help developers running the unit tests know they need to update their data). Just like other caches, Thrift’s `SeqId` must be zeroed out to correctly match requests. Previously we directly modified the `SeqId Int32` within the `Array[Byte]`, this time let’s try an alternative approach (just for kicks), and use `TProtocol`’s built in functions:

```scala
def changeSeqId(
  requestOrResponse: Array[Byte], 
  protocolFactory: TProtocolFactory, 
  seqId: Int = 0): (Int, Array[Byte]) = {
 
  val inputTransport = new TMemoryInputTransport(requestOrResponse)
  val inputProtocol = protocolFactory.getProtocol(inputTransport)
  //pull out the TMessage header
  val inputMessage = inputProtocol.readMessageBegin
  //find all data past the header
  val remainingBytes = inputTransport.getBytesRemainingInBuffer
  val l = requestOrResponse.length
  val remainingInputMessage = requestOrResponse.slice(l - remainingBytes, l)
 
  //construct a new Array[Byte] using a TMemoryBuffer
  val outputTransport = new TMemoryBuffer(requestOrResponse.length)
  val outputProtocol = protocolFactory.getProtocol(outputTransport)
  //replacement TMessage with our SeqId
  val message0 = new TMessage(inputMessage.name, inputMessage.`type`, seqId)
  outputProtocol.writeMessageBegin(message0)
  val requestOrResponse0 = outputTransport.getArray.slice(0, outputTransport.length)
 
  //json protocols expect the next strut to write commas,
  // we need first struct to add it.  Try writing a new empty
  // struct and see if anything is added.
  outputProtocol.writeStructBegin(null)
  val jsonCommaFix = if (outputTransport.length > requestOrResponse0.length)
    //this is a complete hack, we only want the first byte added
    val l = requestOrResponse.length - remainingBytes
    requestOrResponse.slice(l - 1, l)
  else Array[Byte]()
 
  (inputMessage.seqid, requestOrResponse0 ++ jsonCommaFix ++ remainingInputMessage)
}
```

Reflecting on how we just hacked together conditional logic to handle JSON serialization means we’ve written some brittle code. Our code does now however handle both `TBinaryProtocol` and `TJSONProtocol` which is kind of nice, so let’s trudge forward by completing requestResponses.

```scala
lazy val requestResponses: Map[String, Array[Byte]] = jsonLog.map {
  case (request, response) => {
    val request0 = changeSeqId(request.getBytes("UTF-8"), logEncoding)._2
    val reserializedResponse = reserialize(finagleServiceObject, 
      response.getBytes("UTF-8"), thriftEncoding, logEncoding)
    (new String(request0, "UTF-8"), reserializedResponse)
  }
}.toMap
```

This code reads in our `TJSONProtocol` log data and populates a request/response dictionary. The keys are JSON – since it might be helpful to developers to know what’s in the map, but we are choosing to reencode all responses to TBinaryProtocol since that’s what the client expects to be returned.

With the `requestResponses` “cache” prepopulated on initialization, all we need to do is map incoming requests to its entries.
Each request gets its `SeqID` zero’d out, reencoded to JSON, and then compared to what is in the `requestResponses` map.

```scala
def apply(request: ThriftClientRequest, service: Service[ThriftClientRequest, Array[Byte]])
  : Future[Array[Byte]] = {
 
  val (oldSeqId, request0) = changeSeqId(request.message, thriftEncoding)
  val requestKeyJson = new String(reserialize(finagleServiceObject, request0, 
    logEncoding, thriftEncoding), "UTF-8")
  //try and match request
  val response = requestResponses.getOrElse(requestKeyJson, {
    return Future.exception(
      new Exception(s"Request signature not found in mock data: $requestKeyJson"))
  })
  Future.value(changeSeqId(response, thriftEncoding, oldSeqId)._2)
}
```

That’s it. A sample usage, using a hard-coded log, would be

```scala
/**
 * 2 mocked requests:
 *  getOrderIds(1) => (3)
 *  getOrderIds(2) => (8,9)
 */
val log = Seq((
"""[1,"getOrderIds",1,0,{"1":{"i32":1}}]""",
"""[1,"getOrderIds",2,0,{"0":{"lst":["rec",1,{"1":{"i32":3}}]}}]""")
),
(
"""[1,"getOrderIds",1,0,{"1":{"i32":2}}]""",
"""[1,"getOrderIds",2,0,{"0":{"lst":["rec",2,{"1":{"i32":8}},{"1":{"i32":9}}]}}]""")
))
     
val mockedData = new MockJSONDataFilter(TestApi, log)
val service = ClientBuilder()
  .codec(ThriftClientFramedCodec())
  .hosts(new InetSocketAddress("localhost", 10000))
  .hostConnectionLimit(1)
  .build()
val client = new TestApi.FinagledClient(mockedData andThen service)
val fromCache1 = client.getOrderIds(1)
val fromCache2 = client.getOrderIds(2)
```

{%
  include downloadsources.html
  src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Thrift-Client-Side-Caching-to-Speed-Up-Unit-Tests.scala,https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Developer-Friendly-Thrift-Request-Logging.scala"
%}