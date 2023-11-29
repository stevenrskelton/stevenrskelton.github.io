---
#layout: post
title: "Binary Semaphore Filter"
categories:
  - Scala
tags:
  - AsyncExecution
  - Thrift
excerpt_separator: <!--more-->
---

Long-running queries are very taxing on a database because they hold on to resources making them unavailable to other 
requests. And what happens if multiple identical requests are made while one is still running? Is there a natural way
to share the same result, or do each request need to perform their own?  This isn't a simple caching solution, it's
more like a subscription behaviour to a running query if one already exists, if not one is created.<!--more-->

{% include table-of-contents.html height="200px" %}

# Sharing a Result isn't a Cache

Some advanced caches may implement this feature within their cache, we are essentially caching a future result with a 
zero TTL. This is a problem when caches don't handle this operation. Their responsibility is to reduce traffic to the 
origin, and if they allow multiple identical requests through because of a cache miss they aren't performing their job.

The end result can be cached for future calls, but let us consider only the case where data isn't cacheable. Concurrent 
identical requests may or may not get the same result set depending on the database isolation setting, however 
eventual consistency designs will be okay with this optimization. Users will be either returned a fresh result as soon
as it responds from the database, or served an in-flight response faster than if it had started its own. Moreover, no 
extra memory is consumed by a cache, and usage actually decreases as responses are shared.

The key differences are:
- User will always receive current datasets, but it’s possible to return stale results from a cache,
- Users cannot hammer the DB with identical queries, but possible with cache when cache is empty or expired,
- No different configuration required, but cache time needs to configuration and has to be static across all API
  methods.

# Mechanisms to Share a Result

A simple approach is to allow only one request to execute, with others queued. This is efficiently done
with a [semaphore](http://en.wikipedia.org/wiki/Semaphore_%28programming%29).  The semaphore is a standard construct used in multithreading is more advanced than 
`syncronized` in that we can control it directly, and make it able to distinguish non-identical requests.

Not all requests result in a service call, but results are never stored in memory. If two identical requests are made, 
only one request will be served from the database and both share the result. 

## Making a Single Result Work

A problem arises if the results are subtly different.  In the case of Thrift / Finagle, all results will have have the
same identifier as the request, a mechanism that allows the client to match incoming responses to the async requests 
that have been made.

Essentially, the response has a `SeqId` property that must be rewritten to match identical queries across
different requests, but the other data in it populated from the database is the same.

### Thrift Responses

For simplicity, let’s create a reusable `Trait` to make our changes.  The methods in this trait will include helper 
logic internal to Finagle that's unfortunately private.

```scala
trait GetAndSetSeqId {
  def get32(buf: Array[Byte], off: Int) =
    ((buf(off + 0) & 0xff) << 24) |
      ((buf(off + 1) & 0xff) << 16) |
      ((buf(off + 2) & 0xff) << 8) |
      (buf(off + 3) & 0xff)

  def put32(buf: Array[Byte], off: Int, x: Int) {
    buf(off) = (x >> 24 & 0xff).toByte
    buf(off + 1) = (x >> 16 & 0xff).toByte
    buf(off + 2) = (x >> 8 & 0xff).toByte
    buf(off + 3) = (x & 0xff).toByte
  }

  def badMsg(why: String) = Failure(new IllegalArgumentException(why))

  def getAndSetId(buf: Array[Byte], newId: Int): Try[Int] = {
    if (buf.size < 4) return badMsg("short header")
    val header = get32(buf, 0)
    val off = if (header < 0)
      4 + 4 + get32(buf, 4)
    else 4 + header + 1

    if (buf.size < off + 4) return badMsg("short buffer")

    val currentId = get32(buf, off)
    put32(buf, off, newId)
    Success(currentId)
  }
}
```

### Implementation using a Semaphore

The logic to create a semaphore around distinct queries is short, but requires a little understanding
of [Scala Futures](https://github.com/twitter/util/blob/master/util-core/src/main/scala/com/twitter/util/Future.scala).

We’ll start first by defining our storage used to keep track of the currently executing requests. A `Map` of `Future`s
is the obvious choice, as long as it’s thread safe. This will be good enough, let’s not get overly pedantic with sync
locks as the edge cases are harmless.

```scala
val inProcess = new HashMap[String, Future[Array[Byte]]]
  with SynchronizedMap[String, Future[Array[Byte]]]
```

The Map keys will be Base64 encoded MD5Sum’s of the request, they are concise and an easy computation.

```scala
def requestHashKey(request: Array[Byte]): String =
  Base64.encodeBase64String(Hashing.md5.hashBytes(request).asBytes)
```

We are now have to tools, and a `Filter` skeleton that can be easily plugged into the Finagle constructor parameters:

```scala
class BinarySemaphoreFilter
  extends SimpleFilter[Array[Byte], Array[Byte]]
    with GetAndSetSeqId {

  def requestHashKey(request: Array[Byte]): String

  val inProcess = new HashMap[String, Future[Array[Byte]]]
    with SynchronizedMap[String, Future[Array[Byte]]]

  def apply(request: Array[Byte],
            service: Service[Array[Byte], Array[Byte]]): Future[Array[Byte]]
}
```

The `apply` method is the meat of any Finagle Filter, recapping, we want to:

#### 1. Key each request using a 0 SeqId,

```scala
val zeroedSeqId = request.clone
val seqId = getAndSetId(zeroedSeqId, 0) match {
  case Success(v) => v
  case Failure(e) => return Future.exception(e)
}
val key = requestHashKey(zeroedSeqId)
```

#### 2. Check if it’s currently executing,

- if not, update our list, and start execution,
- if so, return the shared Future from our list,

#### 3. When the execution completes, remove it from our list of executing requests,
#### 4. Return a copy of response, with the correct SeqId, to each waiting response.

```scala
inProcess.getOrElseUpdate(key, {
  service(zeroedSeqId).ensure({
    inProcess.remove(key)
  })
}).map(r => {
  val zerodSeqIdResponse = r.clone
  getAndSetId(zerodSeqIdResponse, seqId)
  zerodSeqIdResponse
})
```

# Conclusion

Concurrency libraries may implement this feature by default, such as [ZIO](https://zio.dev/reference/concurrency/), or 
have pre-constructed Map classes to handle it.  Our particular use-case was specific to Finagle and attempted to use
only the very basic `Future` primitive classes, without manually constructing results from a `Promise`. Often times
the simplest solution, in our case a simple `SyncronizedMap` Finagle `Filter` allowed us to make great performance
improvements with very few lines of code. We would expect mature libraries to have different implementations, but ones
more complex and in need of robust unit tests to ensure error free functionality.

{%
include downloadsources.html
src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Binary-Semaphore-Filter.scala"
%}
