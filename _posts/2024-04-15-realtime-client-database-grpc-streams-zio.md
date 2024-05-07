---
title: "Realtime Client Database: gRPC Bi-Directional Streams and ZIO Hub"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
example: realtime-database-zio-hub-grpc
sources:
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/grpcupdate/Main.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/grpcupdate/ZSyncServiceImpl.scala"
  - "/src/test/scala/ca/stevenskelton/examples/realtimeziohubgrpc/grpcupdate/ZSyncServiceImplSpec.scala"
---

Realtime push-based databases such as [Google Firebase](https://firebase.google.com/docs/database) conveniently ensure
clients are synchronized with the server. Data updates stream to clients immediately as they happen; and if a client
disconnects, updates are immediately processed after reconnecting.

[gRPC server streaming](https://grpc.io/docs/what-is-grpc/core-concepts/#server-streaming-rpc)
and [ZIO Hub](https://zio.dev/reference/concurrency/hub/) can implement this functionality replicating an expensive paid
Firebase service while allowing greater extensibility.<!--more-->

{% include multi_part_post.html %}

{% include table-of-contents.html height="100px" %}

# Evolving User Demands for Data Streaming

Typical web-based client-server communication is to have clients initiate all requests to the server. This minimizes
load on the server as very minimal processing needs to be performed between requests. As server technology and hardware
performance have increased, more robust paradigms have evolved to cater to rising user expectations. To reduce
notification latencies, it was imperative to allow server request initiation. Immediately reaction by the server to  
external changes can be set to clients without waiting for manual user initiations or for a polling delay to elapse.

# Bi-Directional gRPC Streaming using HTTP/2

Two technology standards for bi-directional web communications have become standards:
[WebSockets](https://en.wikipedia.org/wiki/WebSocket)
and [HTTP/2 streams](https://datatracker.ietf.org/doc/html/rfc9113#name-streams-and-multiplexing).

## Today's Complementary Web Standards

### WebSockets

WebSockets were created first of the two as an extension to the HTTP/1.1 standard. By allowing clients to issue
an [Upgrade request](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Upgrade) on an established HTTP/1.1
connection, capable servers can switch the connection to use the _websocket_ network protocol, supporting bi-directional
streaming. Since this is an upgrade mechanism, backwards compatibility and fallback support for older clients has less
complication to implement. The WebSocket protocol has a common JavaScript API across all browsers, making it today's
preferred bidirectional protocol within browser use-cases. Despite _websocket_ being an unversioned specification it
has seen improvements over time by way of it being built on top of an HTTP protocol. HTTP/2 significantly improved
connection performance through connection multiplexing, allowing websockets to benefit when clients use multiple
websocket connections simultaneously as they are now being multiplexed over the single upgraded HTTP/2 connection.

### HTTP/2 Streaming

For non-browser communications such as by mobile apps or server-server communications, WebSockets is an unnecessary
layer. WebSockets is an upgraded HTTP connection, implemented by creating an additional layer in the networking
protocol. The changes in HTTP/2 directly addressed bidirectional communication streams, so when the WebSocket API isn't
beneficial, it is optimal to use the HTTP protocol capabilities directly. HTTP/2 streaming is the preferred
bidirectional mechanism for all use-cases without a web browser client or JavaScript dependency.

## gRPC: A Remote Procedure Call (RPC) framework

gRPC is a high-performance networking framework built on top of HTTP/2, supporting multiple programming languages for
both client and server implementation. It encodes network traffic using [Protocol Buffers](https://protobuf.dev/) using
_proto_ files and syntax, which also are used to define the server API exposed to clients.

A gRPC server which will be used for the realtime database will be called _SyncService_, and expose a bidirectional
stream on the _Bidirectional_ endpoint, receiving _Request_ objects and emitting _Response_ objects to their respective
streams.

A _Update_ endpoint has also been defined, this is the typical client-initiated handler, which will receive a
`UpdateRequest` object from the client and return a `UpdateResponse` object to the client.

```protobuf
service SyncService {
  rpc Bidirectional (stream Request) returns (stream Response);
  rpc Update(UpdateRequest) returns (UpdateResponse);
}
```

This maps to the Scala interface:

```scala
  /**
 * Requests will subscribe/unsubscribe to `Data`.
 * Data updates of subscribed elements is streamed in realtime.
 */
def bidirectionalStream(request: Stream[StatusException, SyncRequest]): Stream[StatusException, SyncResponse]

/**
 * Creation / Update of `Data`. Response will indicate success or failure due to write conflict.
 * Conflicts are detected based on the ETag in the request.
 */
def update(request: UpdateRequest): IO[StatusException, UpdateResponse]
```

### Subscription Request and Response Objects

A basic subscription mechanism will allow clients to subscribe and unsubscribe to object updates based on the object id.
The protobuf definition for requests is:

```protobuf
message SyncRequest {
  repeated Subscribe subscribes = 1;
  repeated Unsubscribe unsubscribes = 2;
}
```

The definition for responses is the `Data` object itself:

```protobuf
message SyncResponse {
  Data data = 1;
}
```

Within the server implementation, we will define a _Subscription Manager_ which will remember subscriptions for
a specific client. ZIO has a primitive called [ZIO Hub](https://zio.dev/reference/concurrency/hub/) which allows
objects such as these _Subscription Manager_ to subscribe to a singular, central message queue. The queue will receive
notifications to all `Data` updates, and each client _Subscription Manager_ will subscribe to the Hub and filter for
events of the ids its client has requested. Because this Hub will queue the stream of database changes, we will name
it `journal` since database journals have similar behaviour.

A helpful feature is to return the current `Data` object back in the client response when initiating a subscription. 
This operation has been indicated by a yellow dashed line in the function diagram.

{%
include figure image_path="/assets/images/2024/04/realtime_database.svg"
caption="Realtime database pushing updates to clients using bi-directional gRPC Streams"
img_style="padding: 10px; background-color: white;"
%}

# Data

The `Data` class will represent an arbitrary data record class, code will rely on the presence of an `id` field, here
represented as an `uint32`. While this type doesn't exist in Java, it adds clarity to the API, but as the Protocol
Buffers Documentation [API Best Practices](https://protobuf.dev/programming-guides/api/) indicates, limits to even the
_int64_ addressable range may make
a [string id preferable](https://protobuf.dev/programming-guides/api/#integer-field-for-id). The `field1` field
represents an arbitrary field, it could be extrapolated to have `Data` contain additional fields (`field2`,`field3`
etc.).

```protobuf
message Data {
  uint32 id = 1;
  string field1 = 2;
}
```



# Adding Data Updates

## ETag and Timestamp

ETags are part of the HTTP Specification and exist to reduce network transfer. The HTTP `If-None-Match` header, when
implemented signals that should the response have the same generated ETag that the server should respond with a _HTTP
304 Not Modified_ instead of a 200 Success with a populated body.

The usefulness of an ETag depends on server support: APIs may implement ETag support similar to HTTP Specification and
use it to omit a response body, others may use it internally to return a previous response from its cache, while others
solely include it as a convenience for clients.

Our API will use an ETag to have our server only return a full Data object on subscription if the client either doesn't
have a previous copy (ie: no ETag available) or has a stale version (ie: conflicting ETag).

{%
include figure image_path="/assets/images/2024/04/etag_use.svg"
caption="Conflicting ETag hashcode will result in an update response from the server"
img_style="padding: 10px; background-color: white;"
%}

To support this functionality, as well as many others which may depend on fetch/cache durations, we'll associate an
ETag and last updated time to all `Data` elements by wrapping them in a new `DataRecord` class:

```scala
case class DataRecord(data: Data, lastUpdate: Instant, etag: ETag)
```

```scala
/**
 * Update `databaseRecordsRef` with data in `request`, rejecting any conflicts.
 * Conflicts are based on the included ETag:
 * If the `previousEtag` doesn't match the ETag in the database it is a conflict. 
 * New item creation ignores the `previousEtag` field.
 * All database item creation / updates are emitting to `journal`.
 */
def updateDatabaseRecords(
                           request: UpdateRequest,
                           journal: Hub[Data],
                           databaseRecordsRef: Ref[Map[Int, Data]]
                         ): UIO[UpdateResponse]
```


We will need to call an effect to connect the _Subscription Manager_ to the journal Hub:

```scala
/**
 * Create Stream from database `journal`
 */
def userSubscriptionStream(
                            userSubscriptionsRef: Ref[HashSet[Int]],
                            journal: Hub[Data]
                          ): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]]
```

We will need to connect the

```scala
/**
 * Update `userSubscriptionsRef` with subscription changes.
 */
def modifyUserSubscriptions(
                             syncRequest: SyncRequest,
                             userSubscriptionsRef: Ref[HashSet[Int]],
                             databaseRecords: Map[Int, Data]
                           ): UIO[Seq[SyncResponse]]

```

//TODO:

The `context: AuthenticatedUser` parameter is for a feature exposed by ZIO to handle gRPC metadata. Every gRPC request
is able to provide headers, similar to how HTTP allows request headers. ZIO generates 2 interface