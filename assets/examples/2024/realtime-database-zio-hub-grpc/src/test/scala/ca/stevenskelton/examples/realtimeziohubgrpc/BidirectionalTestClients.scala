package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.SyncServer.HubCapacity
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.ZioSyncService.SyncServiceClient
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse}
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{CallOptions, ManagedChannelBuilder, ServerBuilder, StatusException}
import scalapb.zio_grpc.{SafeMetadata, ServerLayer, ServiceList, ZManagedChannel}
import zio.stream.{Stream, ZStream}
import zio.{Hub, IO, Promise, Ref, Trace, ZIO}

import java.net.ServerSocket
import scala.collection.mutable
import scala.util.Using

class BidirectionalTestClients(
                                stream: Stream[StatusException, (UserId, SyncRequest)],
                                closeAfterResponseCount: Int,
                                clients: Seq[UserId] = Seq(1, 2, 3),
                              )(implicit trace: Trace):

  def calculateEtag(data: Data): DataRecord.ETag = data.id.toString + data.field1
  
  def responses: IO[Throwable, Seq[(UserId, SyncResponse)]] =

    val serverPort = Using(new ServerSocket(0))(_.getLocalPort).get

    for
      hub <- Hub.sliding[DataRecord](HubCapacity)
      database <- Ref.make[mutable.Map[Int, DataRecord]](mutable.Map.empty)
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(serverPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(ZSyncServiceImpl(hub, database).transformContextZIO(SyncServer.authenticatedUserContext)),
        )
        .launch.fork

      streamClosePromise <- Promise.make[StatusException, SyncRequest]
      userResponsesRef <- Ref.make[Seq[(UserId, SyncResponse)]](Nil)
      _ <- clients
        .foldLeft(ZStream.empty):
          (r, userId) => r.merge(buildClient(userId, streamClosePromise, serverPort))

        .runFoldZIO(1):
          case (responseCount, userResponse) =>
            ZIO.log(s"Response: ${userResponse._2.toString}") *>
              userResponsesRef.update {
                userResponses => userResponses :+ userResponse
              } *> {
              if (responseCount >= closeAfterResponseCount) {
                ZIO.log(s"Finalizing client streams") *>
                  streamClosePromise.succeed(SyncRequest.defaultInstance).as(-1)
              } else {
                ZIO.succeed(responseCount + 1)
              }
            }

      userResponses <- userResponsesRef.get

      _ <- hub.shutdown
      _ <- grpcServer.interruptFork
    yield
      userResponses

  end responses

  private def buildClient(
                           userId: UserId,
                           closePromise: Promise[StatusException, SyncRequest],
                           serverPort: Int,
                         ): Stream[Throwable, (UserId, SyncResponse)] =

    val layer = SyncServiceClient.live(
      ZManagedChannel(ManagedChannelBuilder.forAddress("localhost", serverPort).usePlaintext()),
      options = CallOptions.DEFAULT,
      metadata = SafeMetadata.make((SyncServer.MetadataUserIdKey, userId.toString)),
    )

    SyncServiceClient.bidirectionalStream(stream.filter(_._1 == userId).map(_._2) ++ ZStream.fromZIO(ZIO.infinity))
      .interruptWhen(closePromise)
      .provideLayer(layer)
      .map(syncResponse => (userId, syncResponse))

  end buildClient

end BidirectionalTestClients