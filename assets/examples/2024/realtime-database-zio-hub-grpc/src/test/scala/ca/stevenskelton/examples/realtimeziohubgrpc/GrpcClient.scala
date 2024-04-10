package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.SyncServer.{GRPCServerPort, HubCapacity, authenticatedUserContext}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.Subscribe.DataSnapshots
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.ZioSyncService.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse}
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{CallOptions, ManagedChannelBuilder, ServerBuilder, StatusException}
import scalapb.zio_grpc.{SafeMetadata, ServerLayer, ServiceList, ZManagedChannel}
import zio.*
import zio.stream.{Stream, ZStream}

import scala.collection.mutable

object GrpcClient extends ZIOAppDefault {

  override def run: URIO[Any, ExitCode] = {

    def createData(batch: Int): Seq[Data] = Seq(
      Data.of(id = 1, field1 = s"id1-batch$batch"),
      Data.of(id = 2, field1 = s"id2-batch$batch"),
      Data.of(id = 3, field1 = s"id3-batch$batch"),
      Data.of(id = 4, field1 = s"id4-batch$batch"),
      Data.of(id = 5, field1 = s"id5-batch$batch"),
    )

    // Client 1:
    //  - watch 1
    //  - update 1,2,3,4
    //  - update 1,2,3,4
    //  - update 1,2,3,4

    // Client 2:
    //  - watch 1,2

    // Client 3:
    //  - watch 1,3

    val streamActions = Seq(
      (1, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 0)))))),
      (2, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 0)))))),
      (2, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(2, 0)))))),
      (3, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 0)))))),
      (3, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(3, 0)))))),
      (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = createData(1))))),
      (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = createData(2))))),
      (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = createData(3))))),
    )

    val zioBlock = for
      _ <- ZIO.log("Starting")
      hub <- Hub.sliding[DataInstant](HubCapacity)
      database <- Ref.make[mutable.Map[Int, DataInstant]](mutable.Map.empty)
      grpcServer <- ServerLayer.fromServiceList(
        ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
        ServiceList.add(SyncServiceImpl(hub, database).transformContextZIO(SyncServer.authenticatedUserContext)),
      ).launch.fork

      streamClosePromise <- Promise.make[StatusException, SyncRequest]
      client = Clients(ZStream.fromIterable(streamActions), streamClosePromise)

      userResponsesRef <- Ref.make[Seq[(Int, SyncResponse)]](Nil)
      _ <- client.output
        .runFoldZIO(1) {
          case (responseCount, userResponse) =>
            ZIO.log(s"Response: ${userResponse._2.toString}") *>
              userResponsesRef.update {
                userResponses => userResponses :+ userResponse
              } *> {
              if (responseCount >= 15) {
                ZIO.log(s"Finalizing client streams") *>
                  streamClosePromise.succeed(SyncRequest.defaultInstance).as(-1)
              } else {
                ZIO.succeed(responseCount + 1)
              }
            }
        }
      userResponses <- userResponsesRef.get
      _ <- hub.shutdown
      _ <- grpcServer.interruptFork
    yield {
      println(s"Total responses: ${userResponses.length}")
      println(s" - user 1: ${userResponses.count(_._1 == 1)}")
      println(s" - user 2: ${userResponses.count(_._1 == 2)}")
      println(s" - user 3: ${userResponses.count(_._1 == 3)}")
      Seq(1, 2, 3).foreach {
        userId =>
          println()
          println(s"User $userId:")
          userResponses.filter(_._1 == userId).foreach {
            (_, response) => println(response.toString)
          }
      }
      ExitCode.success
    }
    zioBlock.exitCode
  }

}

class Clients(
               stream: Stream[StatusException, (Int, SyncRequest)],
               streamClosePromise: Promise[StatusException, SyncRequest],
             )(implicit trace: Trace) {

  def buildClient(userId: Int): Stream[Throwable, (Int, SyncResponse)] = {
    val layer = SyncServiceClient.live(
      ZManagedChannel(ManagedChannelBuilder.forAddress("localhost", SyncServer.GRPCServerPort).usePlaintext()),
      options = CallOptions.DEFAULT,
      metadata = SafeMetadata.make(("user-id", userId.toString)),
    )
    SyncServiceClient.bidirectionalStream(stream.filter(_._1 == userId).map(_._2) ++ ZStream.fromZIO(ZIO.infinity))
      .interruptWhen(streamClosePromise)
      .provideLayer(layer)
      .map(syncResponse => (userId, syncResponse))
  }

  val output: Stream[Throwable, (Int, SyncResponse)] = buildClient(1).merge(buildClient(2)).merge(buildClient(3))
}