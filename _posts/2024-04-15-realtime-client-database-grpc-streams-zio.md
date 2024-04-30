---
title: "Realtime Client Database using gRPC Bi-Directional Streams and ZIO Hub"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
example: realtime-database-zio-hub-grpc
sources:
  - "/src/main/protobuf/sync_service.proto"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/grpcupdate/Main.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/grpcupdate/ZSyncServiceImpl.scala"
  - "/src/test/scala/ca/stevenskelton/examples/realtimeziohubgrpc/grpcupdate/ZSyncServiceImplSpec.scala"
---

Realtime pushed-based databases such as [Google Firebase](https://firebase.google.com/docs/database) are a convenient
way to ensure clients have the most recent data locally. Data updates are automatically streamed to clients
immediately as they happen, or in the case of a client disconnect, immediately after reconnecting.

[gRPC server streaming](https://grpc.io/docs/what-is-grpc/core-concepts/#server-streaming-rpc)
and [ZIO Hub](https://zio.dev/reference/concurrency/hub/) allow this functionality to be easily replicated and
customized beyond what expensive paid-for services such as Firebase can do.<!--more-->

{% include table-of-contents.html height="100px" %}

# Client demands for data streaming

The typical simple web-based client-server communication pattern is for data to be requested by the client. When
clients want new data or otherwise interact, it will initiate a new request to the server. But as server technology
and hardware capacities have increased, user expectations have increased to expect all client UIs to present realtime
data without the friction of manually requesting data updates. The typical client-server communication is slowly
evolving into the stream of data in both directions between the client and server.

# gRPC Server Bi-Directional Streaming using HTTP/2

The evolution of technology has resulted in two technology standards for web-based bi-directional communications:  
[WebSockets](https://en.wikipedia.org/wiki/WebSocket) and HTTP/2 streams.

WebSockets were created first as the ability for a standard HTTP/1.1 connection to upgrade to support bi-directional
client-server streaming. This is still the best approach for browser-server communications because of its clear
JavaScript APIs within all browsers, backwards compatibility for HTTP/1.1-only clients, and its ability to take
advantage of performance improvements offered by HTTP/2 and beyond.

For non-browser communications, such as with mobile apps or inter-server communication WebSockets is an unnecessary
layer. As WebSockets runs over HTTP, because HTTP/2 has directly integrated multiplexed streaming capabilities, it is
better for abstraction libraries such as gRPC to directly support HTTP/2 instead of the higher-level WebSocket layer.

```protobuf
service SyncService {
  rpc Bidirectional (stream Request) returns (stream Response);
}
```

```scala
def bidirectional(request: Stream[StatusException, Request]): Stream[StatusException, Response] =
//request.flatMap:
//  Request => Stream[StatusException, Response]
```

## ZIO Hub for Concurrency and Subscriptions

```protobuf
message SyncRequest {
  repeated Subscribe subscribes = 1;
  repeated Unsubscribe unsubscribes = 2;
}
message SyncResponse {
  Data data = 1;
}
```

//TODO:

{%
include figure image_path="/assets/images/2024/04/realtime_database.svg"
caption="Realtime database pushing updates to clients using bi-directional gRPC Streams"
img_style="padding: 10px; background-color: white; height: 320px;"
%}

# Data

The `Data` class will represent an arbitrary data record class, code will rely on the presence of an `id` field, here
represented as an `uint32`. While this type doesn't exist in Java, it adds clarity to the API, but as the Protocol
Buffers Documentation [API Best Practices](https://protobuf.dev/programming-guides/api/) indicates, limits to even the
_int64_ addressable range may make
a [string id preferable](https://protobuf.dev/programming-guides/api/#integer-field-for-id). The `field1` field is
unused in the sample code beyond ETag validation unit tests.

```protobuf
message Data {
  uint32 id = 1;
  string field1 = 2;
}
```

## ETag and Timestamp

ETags are the part of the HTTP Specification and exist to reduce network transfer. The HTTP `If-None-Match` header, when
implemented signals that should the response have the same generated ETag that the server should respond with a _HTTP
304 Not Modified_ instead of a 200 Success with a populated body.  

The usefulness of an ETag depends on server support: APIs may implement ETag support similiar to HTTP Specification and
use it to omit a response body, others may use it internally to return a previous response from its cache, while others 
solely include it as a convenience for clients.

Our API will use an ETag to have our server only return a full Data object on subscription if the client either doesn't
have a previous copy (ie: no ETag available) or has a stale version (ie: conflicting ETag).

{%
include figure image_path="/assets/images/2024/04/etag_use.svg"
caption="Conflicting ETag hashcode will result in an update response from the server"
img_style="padding: 10px; background-color: white; height: 320px;"
%}

To support this functionality, as well as many others which may depend on fetch/cache durations, we'll associate an 
etag and last updated time to all `Data` elements by wrapping them in a new `DataRecord` class:

```scala
case class DataRecord(data: Data, lastUpdate: Instant, etag: ETag)
```

## External Datasource

The focus of this article is the client-server communication, not the external datasource, so a very basic interface 
will suffice:

```scala
trait ExternalData {

  //Fetches `Data` from external datasource, 
  // and if different than in cache update cache and queue message all subscribers
  def queueFetchAll(ids: Seq[Int]): Unit
  
  //Returns all actively subscribed ids
  def subscribedIds: ZIO[Any, Nothing, Set[Int]]
  
  
}
```

