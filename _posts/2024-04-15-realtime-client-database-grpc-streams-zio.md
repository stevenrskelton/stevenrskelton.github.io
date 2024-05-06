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

Realtime pushed-based databases such as [Google Firebase](https://firebase.google.com/docs/database) are a convenient
way to ensure clients have the most recent data locally. Data updates are automatically streamed to clients
immediately as they happen, or in the case of a client disconnect, immediately after reconnecting.

[gRPC server streaming](https://grpc.io/docs/what-is-grpc/core-concepts/#server-streaming-rpc)
and [ZIO Hub](https://zio.dev/reference/concurrency/hub/) allow this functionality to be easily replicated and
customized beyond what expensive paid-for services such as Firebase can do.<!--more-->

{%
include multi_part_post.html
series="Realtime Client Database"
p1="2024-04-15-realtime-client-database-grpc-streams-zio"
p2="2024-05-01-realtime-client-database-external-datasource-zlayer"
%}

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
  /**
 * Requests will subscribe/unsubscribe to `Data`.
 * Data updates of subscribed elements is streamed in realtime.
 */
def bidirectionalStream(
                         request: Stream[StatusException, SyncRequest], 
                         context: AuthenticatedUser,
                       ): Stream[StatusException, SyncResponse]
//request.flatMap: Request => Stream[StatusException, Response]

/**
 * Creation / Update of `Data`. Response will indicate success or failure due to write conflict.
 * Conflicts are detected based on the ETag in the request.
 */
def update(
            request: UpdateRequest, 
            context: AuthenticatedUser
          ): IO[StatusException, UpdateResponse]
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

The usefulness of an ETag depends on server support: APIs may implement ETag support similar to HTTP Specification and
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
ETag and last updated time to all `Data` elements by wrapping them in a new `DataRecord` class:

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

# Common Helper Effects

```scala
/**
 * Create Stream from database `journal`
 */
def userSubscriptionStream(
                            userSubscriptionsRef: Ref[HashSet[Int]], 
                            journal: Hub[DataRecord]
                          ): ZIO[Scope, Nothing, Stream[StatusException, SyncResponse]]

/**
 * Update `databaseRecordsRef` with data in `request`, rejecting any conflicts.
 * Conflicts are based on the included ETag:
 * If the `previousEtag` doesn't match the ETag in the database it is a conflict. 
 * New item creation ignores the `previousEtag` field.
 * All database item creation / updates are emitting to `journal`.
 */
def updateDatabaseRecords(
                           request: UpdateRequest, 
                           journal: Hub[DataRecord], 
                           databaseRecordsRef: Ref[Map[Int, DataRecord]]
                         ): UIO[UpdateResponse]

/**
 * Update `userSubscriptionsRef` with subscription changes.
 */
def modifyUserSubscriptions(
                             syncRequest: SyncRequest, 
                             userSubscriptionsRef: Ref[HashSet[Int]], 
                             databaseRecords: Map[Int, DataRecord]
                           ): UIO[Seq[SyncResponse]]

```