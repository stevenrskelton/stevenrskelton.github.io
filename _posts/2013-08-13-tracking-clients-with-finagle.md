---
#layout: post
title: "Tracking Clients with Finagle"
categories:
  - Platform
  - Scala
tags:
  - Thrift
---

In a Service Oriented Architecture, a service may be used by many different clients – each with different usage
patterns and performance profiles. Behind a corporate firewall, without each client authenticating itself to our server,
how can monitor a specific client if we can’t identify their requests?

One way would be to track each client’s IP, but servers change, and it may be impossible to coordinate across teams.
Another would way is to push the logging and monitoring responsibility to each and every client. However, the easiest way
would be to watermark each thrift request with client information, but does the standard thrift protocol allow it? The
answer is yes, a small amount client information can be transmitted unobtrusively dual-purposing the randomly
generated `SeqId`.

The `SeqId` is a randomly generated `Int32` used to uniquely identify each request. As the particular random value chose
for any request is inconsequential, we can partition the SeqId values across clients, thus clearly identifying the
origin of each request.

Under the covers, every Finagle client uses
a [Filter](https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/Filter.scala)
class
called [SeqIdFilter](https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/scala/com/twitter/finagle/thrift/SeqIdFilter.scala)
class to set the `SeqId` of each request.

So, if we add additional parameters to partition the randomly generated `SeqId`:

```scala
/** Creates SeqId that modulo to the clientId */
class MultiClientSeqIdFilter(clientId: Int, numberOfClients: Int = 100)
  extends SeqIdFilter {
 
  import SeqIdFilter._
 
  require(numberOfClients > 0, "Number of clients must be greater than zero")
  require(clientId >= 0, "clientId must be >= 0")
  require(clientId < numberOfClients, "clientId must be < numberOfClients")
 
  val intRange = (2147483647 - numberOfClients) / numberOfClients
 
  override def apply(
    req: ThriftClientRequest,
    service: Service[ThriftClientRequest, Array[Byte]])
  : Future[Array[Byte]]
}
```

The only code changes required to this class (besides copying in all `private[this]` functions) is to change the id
generation, from

```scala
val id = rng.nextInt()
```

to:

```scala
val id = rng.nextInt(intRange) * numberOfClients + clientId
```

Now, all clients must register the `MultiClientSeqIdFilter` filter using their assigned `clientId`, and it also makes
sense to disable the old `SeqIdFilter`.
The old `SeqIdFilter` can be disabled by passing `useCallerSeqIds=true` to the `ThriftClientFramedCodecFactory`
constructor.

```scala
val appClientId = 3
val filter = new MultiClientSeqIdFilter(appClientId)
 
val codecFactory = new ThriftClientFramedCodecFactory(None,
  true, new TBinaryProtocol.Factory())
 
val clientService = ClientBuilder()
  .codec(codecFactory)
  .hosts(new InetSocketAddress("localhost", 10000))
  .hostConnectionLimit(2)
  .build()
```

Any clients that do not implement partitioned `SeqId` will obviously contaminate your statistics, however all other
functionality will be unaffected.
The server can now use its own `Filters` to collect client usage statistics, and for
this [Ostrich](https://github.com/twitter/ostrich) is perfect.

Two useful statistics not part of the default [Finagle Ostrich](https://github.com/twitter/ostrich) configuration are:

1. When was each method last called?
2. How is each method performing?

Having these two statistics broken down by client provides a high level picture of how your API is being used by each
client.

```scala
import com.twitter.ostrich.stats.Stats
 
class ApiClientUsageStats(
  clientIds: Map[Int, String] = Map(),
  numberOfClients: Int = 100)
  extends SimpleFilter[Array[Byte], Array[Byte]] {
 
  /** Record any API method invocations in Ostrich. */
  def apply(request: Array[Byte], service: Service[Array[Byte], Array[Byte]])
  : Future[Array[Byte]] = {
 
    val inputTransport = new TMemoryInputTransport(request)
    val binaryProtocol = new TBinaryProtocol(inputTransport)
    val msg = binaryProtocol.readMessageBegin
    val name = msg.name
    val clientId = msg.seqid % numberOfClients
    val client = clientIds.getOrElse(clientId, clientId.toString)
 
    val now = (new java.util.Date).toString
    Stats.setLabel(s"Last Call: `$name`", now)
    Stats.setLabel(s"Last Call: `$name` by $client: ", now)
    Stats.timeFutureMillis(s"API: `$name`") {
      Stats.timeFutureMillis(s"API: `$name` by $client") {
        service(request)
      }
    }
  }
}
```

So, if we add this new filter to our server:

```scala
val apiUserageFilter = new ApiClientUsageStats(Map(1 -> "client1", 2 -> "client2", 3 -> "client3"))
```

After some client activity, visiting our Ostrich `/stats.txt` page, we see

```
labels:
  Last Call: `getUserName`: Sun Aug 11 17:00:57 EDT 2013
  Last Call: `getUserName` by client1: Sun Aug 11 17:00:57 EDT 2013
  Last Call: `getUserName` by client2: Sun Aug 11 13:30:34 EDT 2013
  Last Call: `getUserName` by client3: Sun Aug 11 16:59:12 EDT 2013
metrics:
  API: `getUserName` by client1_msec: (average=568, count=1, maximum=568,
minimum=568, p50=568, p90=568, p95=568, p99=568, p999=568, p9999=568, sum=568)
  API: `getUserName` by client2_msec: (average=229, count=1, maximum=229,
minimum=229, p50=229, p90=229, p95=229, p99=229, p999=229, p9999=229, sum=229)
  API: `getUserName` by client3_msec: (average=302, count=1, maximum=302,
minimum=302, p50=302, p90=302, p95=302, p99=302, p999=302, p9999=302, sum=302)
  API: `getUserName`_msec: (average=366, count=3, maximum=568,
minimum=229, p50=302, p90=568, p95=568, p99=568, p999=568, p9999=568, sum=1099)
```

{%
include downloadsources.html
src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Tracking-Clients-with-Finagle.scala"
%}
