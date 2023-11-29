---
#layout: post
title: "Binary Semaphore Filter"
categories:
  - Scala
tags:
  - AsyncExecution
excerpt_separator: <!--more-->
---

Long-running queries are very taxing on a database. But caching idempotent queries may not always be a suitable
solution. What happens if queries run for N-seconds, but users expect to see new changes immediately? What happens if
queries return large datasets that won’t all fit into memory?<!--more-->

There is a middle ground. Not all requests result in a service call, but results are never stored in memory. If two
identical requests are made, only one request will be served from the database and both share the result. Users will be
either returned a fresh result, or served faster than a regular database call. Moreover, no extra memory is consumed by
a cache, and usage actually decreases as responses are shared.

Comparing our [semaphore](http://en.wikipedia.org/wiki/Semaphore_%28programming%29) approach to a N-second query cache (
results are added to cache on response), we have the following key differences:

- User will always receive current datasets, but it’s possible to return stale results from a cache,
- Users cannot hammer the DB with identical queries, but possible with cache when cache is empty or expired,
- No different configuration required, but cache time needs to configuration and has to be static across all API
  methods.

As encountered before when creating our caching solutions, the SeqId must be rewritten to match identical queries across
different requests.
For simplicity, let’s create a reusable Trait to put our previous code (this code can also be found in finagle, but it’s
private):

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

We are now have to tools, and a `Filter` skeleton:

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

1. key each request using a 0 SeqId,

```scala
val zeroedSeqId = request.clone
val seqId = getAndSetId(zeroedSeqId, 0) match {
  case Success(v) => v
  case Failure(e) => return Future.exception(e)
}
val key = requestHashKey(zeroedSeqId)
```

2. check if it’s currently executing,

- if not, update our list, and start execution,
- if so, return the shared Future from our list,

3. when the execution completes, remove it from our list of executing requests,
4. return a copy of response, with the correct SeqId, to each waiting response.

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
}
```

{%
include downloadsources.html
src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Binary-Semaphore-Filter.scala"
%}
