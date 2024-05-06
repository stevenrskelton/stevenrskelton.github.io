---
title: "Realtime Client Database: Performance Testing in the Cloud"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
example: realtime-database-zio-hub-grpc
sources:
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/Main.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImpl.scala"
  - "/src/test/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ZSyncServiceImplSpec.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ExternalDataLayer.scala"
  - "/src/main/scala/ca/stevenskelton/examples/realtimeziohubgrpc/externaldata/ClockExternalDataLayer.scala"
---

//TODO
<!--more-->

{% include multi_part_post.html %}

The testing client for a performance test can be the same as one created for local unit tests.

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