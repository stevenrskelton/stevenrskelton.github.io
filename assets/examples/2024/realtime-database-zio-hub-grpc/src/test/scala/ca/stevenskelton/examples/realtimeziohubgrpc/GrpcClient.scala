package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.ZioSyncService.SyncServiceClient
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse, UpdateRequest, UpdateResponse}
import io.grpc.{CallOptions, ManagedChannelBuilder}
import scalapb.zio_grpc.{SafeMetadata, ZManagedChannel}
import zio.stream.{Stream, ZStream}
import zio.{IO, Queue, Scope, UIO, ZIO, ZLayer}

case class GrpcClient(
                       userId: UserId,
                       requests: Queue[SyncRequest],
                       responses: Stream[Throwable, (UserId, SyncResponse)],
                       grpcLayer: ZLayer[Any, Throwable, SyncServiceClient],
                     ):
  
  def update(request: UpdateRequest): IO[Throwable, UpdateResponse] = 
    SyncServiceClient.update(request).provideLayer(grpcLayer)
    
//  def sync(count: Int, request: SyncRequest): IO[Throwable, Seq[SyncResponse]] = {
//    requests.offer(request) *> responses.take(count).map(_._2)
//  }

object GrpcClient:
  def launch(
             userId: UserId,
             serverPort: Int,
           ): ZIO[Scope, Nothing, GrpcClient] = ZIO.scoped(Queue.unbounded[SyncRequest]).map:

    requests =>

      val grpcLayer: ZLayer[Any, Throwable, SyncServiceClient] = SyncServiceClient.live(
        ZManagedChannel(ManagedChannelBuilder.forAddress("localhost", serverPort).usePlaintext()),
        options = CallOptions.DEFAULT,
        metadata = SafeMetadata.make((SyncServer.MetadataUserIdKey, userId.toString)),
      )

      val responses = SyncServiceClient
        .bidirectionalStream(ZStream.fromQueue(requests))
        .provideLayer(grpcLayer)
        .map((userId, _))

      GrpcClient(userId, requests, responses, grpcLayer)

