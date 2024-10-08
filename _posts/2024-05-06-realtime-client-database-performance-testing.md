---
title: "Realtime Client Database: Performance Testing in the Cloud"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
  - gRPC
excerpt_separator: <!--more-->
example: realtime-database-zio-hub-grpc
sources:
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/Main.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImpl.scala"
  - "/src/test/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImplSpec.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ExternalDataLayer.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/performance/ClockExternalDataLayer.scala"
todo: "Better example for testing"
---

//TODO
<!--more-->

{% include multi_part_post.html %}

The testing client for a performance test can be the same as one created for local unit tests.

## Performance Testing Implementation

A sample implementation for performance testing would timestamp on `Data` element updates allowing clients to compare
time lag between server updates and their notification of it.

Performance testing implementation and results are covered in [Realtime Client Database Performance Testing]({% post_url
2024-05-06-realtime-client-database-performance-testing %}).

```scala
class ClockExternalDataLayer private(clock: Clock, refreshSchedule: Schedule[Any, Any, Any])
  extends ExternalDataLayer(refreshSchedule) {

  override protected def externalData(
                                       chunk: NonEmptyChunk[Either[DataId, DataRecord]]
                                     ): UIO[Chunk[Data]] = {
    clock.instant.map {
      now =>
        val dataId = either.fold(identity, _.data.id)
        chunk.map(either => Data.of(dataId, now.toString))
    }
  }
}
```



```scala
case class GrpcClient(
                       userId: UserId,
                       requests: Queue[SyncRequest],
                       responses: Stream[Throwable, (UserId, SyncResponse)],
                       grpcLayer: ZLayer[Any, Throwable, SyncServiceClient],
                     ) {

  def update(request: UpdateRequest): IO[Throwable, UpdateResponse] = {
    SyncServiceClient.update(request).provideLayer(grpcLayer)
  }
}
```

```scala
object GrpcClient {
  def launch(
              userId: UserId,
              serverAddress: String,
              serverPort: Int,
            ): ZIO[Scope, Nothing, GrpcClient] = ZIO.scoped(Queue.unbounded[SyncRequest]).map {

    requests =>

      val grpcLayer: ZLayer[Any, Throwable, SyncServiceClient] = SyncServiceClient.live(
        ZManagedChannel(ManagedChannelBuilder.forAddress(serverAddress, serverPort).usePlaintext()),
        options = CallOptions.DEFAULT,
        metadata = SafeMetadata.make((AuthenticatedUser.MetadataUserIdKey, userId.toString)),
      )

      val responses = SyncServiceClient
        .bidirectionalStream(ZStream.fromQueue(requests))
        .provideLayer(grpcLayer)
        .map((userId, _))

      GrpcClient(userId, requests, responses, grpcLayer)
  }
}
```

Data ids: 
1 - 600 are second markers.
601 - 1000 are random

Clients:
Subscribe 1 - 600, subscribe i mod clientid