package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.ZioSyncService.SyncServiceClient
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse}
import io.grpc.{CallOptions, ManagedChannelBuilder}
import scalapb.zio_grpc.{SafeMetadata, ZManagedChannel}
import zio.stream.{Stream, ZStream}
import zio.{Queue, Scope, ZIO}

case class GrpcClient(
                       userId: UserId,
                       requests: Queue[SyncRequest],
                       responses: Stream[Throwable, (UserId, SyncResponse)],
                     )

object GrpcClient:
  def launch(
             userId: UserId,
             serverPort: Int,
           ): ZIO[Scope, Nothing, GrpcClient] = ZIO.scoped(Queue.unbounded[SyncRequest]).map:

    requests =>

      val layer = SyncServiceClient.live(
        ZManagedChannel(ManagedChannelBuilder.forAddress("localhost", serverPort).usePlaintext()),
        options = CallOptions.DEFAULT,
        metadata = SafeMetadata.make((SyncServer.MetadataUserIdKey, userId.toString)),
      )

      val responses = SyncServiceClient
        .bidirectionalStream(ZStream.fromQueue(requests))
        .provideLayer(layer)
        .map((userId, _))

      GrpcClient(userId, requests, responses)

