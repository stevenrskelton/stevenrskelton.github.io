---
#layout: post
title: "Finagle Query Cache with Guava"
categories:
  - DevOps/Platform
  - Scala
tags:
  - Thrift
---

For many data services, any easy way to reduce database load is to cache calls to semi-static data (ie: append-only, or
refreshed only on a set schedule), and very recent calls due to backward user navigation. Not all methods and data are
suitable for caching, so any implementation will require the ability to be selective.

Using Finagle’s Filters we can configure our caching in a central place, leaving individual method implementations clean
of the concern.

The heavy lifting of our cache will be handled
by [Google Core Libraries: Guava](https://code.google.com/p/guava-libraries/), leaving us to sort out the small details.

Let’s start off writing a generic abstract filter class that will allow us to specify what method calls we want to
cache. Here we are using an `Option` to allow us to cache all method calls if we pass in `None`.

```scala
import com.twitter.finagle.{ Service, SimpleFilter }
import com.twitter.util.Future
import com.google.common.cache.Cache
 
abstract class AbstractCacheFilter(val methodsToCache: Option[Seq[String]] = None) 
  extends SimpleFilter[Array[Byte], Array[Byte]] {
 
  import com.google.common.hash.Hashing
  import org.apache.commons.codec.binary.Base64
 
  /** Hash of request to use as key in cache. */
  def requestHashKey(request: Array[Byte]): String =
    Base64.encodeBase64String(Hashing.md5.hashBytes(request).asBytes)
 
  val cache: Cache[String, Array[Byte]]
 
  def apply(request: Array[Byte], 
    service: Service[Array[Byte], Array[Byte]]): Future[Array[Byte]]
}
```

The abstract `cache` has been keyed using a `String`, and `requestHashKey` will be used to uniquely identify all request
queries.
The question is: are identical queries represented by the same bytes? The answer is, in the case of Thrift, no – all
requests are uniquely identified using a random `SeqId`. The `SeqId` is a unique `Int32` assigned to each request,
allowing multiple requests to be pipelined over the same network connection. But it isn’t a serious problem, we know how
to find it in the protocol, and thankfully it’s easy to change.

Finagle uses
the [TBinaryProtocol](https://github.com/apache/thrift/blob/master/lib/java/src/org/apache/thrift/protocol/TBinaryProtocol.java),
so all of this code also makes that assumption. The `SeqId` is a 4 byte integer, and is the third field in the binary
protocol: right after the version and method name. We will need the ability to change this value in both the requests,
and the responses. We’ll choose to store all requests/responses in our cache with `SeqId=0`, and then change them in any
cached response before it’s returned to the client.

```scala
def bytesToInt(bytes: Array[Byte]): Int = java.nio.ByteBuffer.wrap(bytes).getInt
 
def binaryProtocolChangeSeqId(requestOrResponse: Array[Byte], 
  seqId: Array[Byte] = Array(0, 0, 0, 0)): Array[Byte] = {
 
  val methodNameLength = bytesToInt(requestOrResponse.slice(4, 8))
  val retVal = new Array[Byte](requestOrResponse.length)
  requestOrResponse.copyToArray(retVal)
  seqId.copyToArray(retVal, 8 + methodNameLength, 4)
  retVal
}
```

We can similarly discover the `SeqId` in any client request:

```scala
def binaryProtocolMethodNameSeqId(request: Array[Byte]): (String, Array[Byte]) = {
  val methodNameLength = bytesToInt(request.slice(4, 8))
  val methodName = new String(request.slice(8, 8 + methodNameLength), "UTF-8")
  val seqId = request.slice(8 + methodNameLength, 12 + methodNameLength)
  (methodName, seqId)
}
```

We now have code to peek into request method names, and to set/zero-out uniquely identifying SeqIds. But before we can
write our caching code, we need to consider the case of Exceptions. It would be unwise to store exceptions into our
cache, so let’s dig further into the Thrift protocol. Thrift allows exceptions to be sent back as part of a valid
response, and any service methods allowing exception responses have a single byte _exit status_ field.

```scala
/** Non-zero status is a thrown error */
def binaryResponseExitStatus(response: Array[Byte]): Byte = {
  val methodNameLength = bytesToInt(response.slice(4, 8))
  //skip over: version,methodLength,method,seqId,msgType,successStruct
  val positionMessageType = 4 + 4 + methodNameLength + 4 + 1 + 1
  response(positionMessageType)
}
```

There is a `msgType` field as part of the protocol which also indicates exceptions, however it is only used for
unhandled exceptions. Any unhandled exception should occur before our caching code and terminate execution, as a result
the rest of our code would not be executed. So we do not have to worry about handling these exception types within our
code.

And finally, our `apply` method:

```scala
def apply(request: Array[Byte], 
  service: Service[Array[Byte], Array[Byte]]): Future[Array[Byte]] = {
 
  val (methodName, seqId) = binaryProtocolMethodNameSeqId(request)
  //cache all, or those specified
  if (!methodsToCache.isDefined || methodsToCache.get.contains(methodName)) {
    //key using a SeqId == 0
    val key = requestHashKey(binaryProtocolChangeSeqId(request))
    val cachedResult = cache.getIfPresent(key)
    if (cachedResult == null) {
      service(request).onSuccess(r => {
        //store with SeqId == 0, if clean exit
        if (binaryResponseExitStatus(r) == 0) 
          cache.put(key, binaryProtocolChangeSeqId(r))
      })
    } else {
      //change the SeqId to match request
      Future.value(binaryProtocolChangeSeqId(cachedResult, seqId))
    }
  } else service(request)
}
```

With our abstract caching filter complete, let’s create some example implementations to illustrate how to handle
different scenarios and put to use some of Guava’s features. Guava is well documented, and there is a set of slides for
the impatient.

Most eventually consistent datasets will benefit from having their recent read queries cached without having to worry
about cache invalidation, as long as their total expiry time is less than the eventual consistency guarantee.

```scala
import com.google.common.cache.CacheBuilder
import com.twitter.conversions.time._
import com.twitter.util.Duration
 
class ExpiryCache(
  val slidingExpiry: Duration = 60 seconds,
  val maxExpiry: Duration = 10 minutes) 
  extends AbstractCacheFilter(None) {
 
  import java.util.concurrent.TimeUnit.SECONDS
  val cache = CacheBuilder.newBuilder()
    .expireAfterAccess(slidingExpiry.inUnit(SECONDS), SECONDS)
    .expireAfterWrite(maxExpiry.inUnit(SECONDS), SECONDS)
    .build[String, Array[Byte]]()
}
```

Next, we will take advantage of the `weighting` functionality in Guava to make a fixed memory size cache for our static
data. Only a small subset of our read queries are for static, historical data, so eligible methods will be specified by
name.

```scala
class FixedSizeCache(methodsToCache: Seq[String], val maxSizeMegabytes: Int = 100) 
  extends AbstractCacheFilter(Some(methodsToCache)) {
 
  import com.google.common.cache.Weigher
  val weight = maxSizeMegabytes * 1048576
  val weigher = new Weigher[String, Array[Byte]]() {
    def weigh(k: String, g: Array[Byte]): Int = g.length
  }
  val cache = CacheBuilder.newBuilder()
    .maximumWeight(weight).weigher(weigher)
    .build[String, Array[Byte]]()
}
```

As a final note, an assumption has been made that the `Array[Byte]` is encoded `TBinaryProtocol`. Alternatively,
a `TProtocolFactory` could have been supplied in the `AbstractCacheFilter` constructor, and fields such as `SeqId` could
be obtained by calling `readMessageBegin`. This alternative approach is revisited later
in [Thrift Client-Side Caching to Speed Up Unit Tests]({% post_url
2013-08-13-thrift-client-side-caching-to-speed-up-unit-tests %}).

{%
include downloadsources.html
src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Finagle-Query-Cache-with-Guava.scala"
%}
