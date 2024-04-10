package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.SyncServer.{GRPCServerPort, HubCapacity, authenticatedUserContext}
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.Subscribe.DataSnapshots
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.ZioSyncService.*
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest, SyncResponse}
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{CallOptions, ManagedChannelBuilder, ServerBuilder, StatusException}
import scalapb.zio_grpc.{SafeMetadata, ServerLayer, ServiceList, ZManagedChannel}
import zio.*
import zio.stream.ZStream
import zio.stream.Stream

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

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
    val actionStream = ZStream.fromIterable(streamActions)
    val client = Clients(actionStream)

    val zioBlock = for
      _ <- ZIO.log("Starting")
      hub <- Hub.sliding[DataInstant](HubCapacity)
      database <- Ref.make[mutable.Map[Int, DataInstant]](mutable.Map.empty)
      grpcServer <- ServerLayer.fromServiceList(
        ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
        ServiceList.add(SyncServiceImpl(hub, database).transformContextZIO(SyncServer.authenticatedUserContext)),
      ).launch.fork
      _ <- client.output
        .runForeach {
          syncResponse => ZIO.log(s"Response: ${syncResponse.toString}")
        }
      _ <- ZIO.log("End requests")
      _ <- grpcServer.interruptFork.exit
      _ <- ZIO.log("End GRPC server")
      _ <- ZIO.log(client.client1Responses.map(_.toString).mkString("Client1: ", "\n", ""))
      _ <- ZIO.log(client.client2Responses.map(_.toString).mkString("Client2: ", "\n", ""))
      _ <- ZIO.log(client.client3Responses.map(_.toString).mkString("Client3: ", "\n", ""))
    yield {

    }
    zioBlock.exitCode
  }

}

class Clients(stream: Stream[StatusException, (Int, SyncRequest)]) {

  val client1Responses: ListBuffer[SyncResponse] = ListBuffer[SyncResponse]()
  val client2Responses: ListBuffer[SyncResponse] = ListBuffer[SyncResponse]()
  val client3Responses: ListBuffer[SyncResponse] = ListBuffer[SyncResponse]()

  def clientLayer(userId: Int): Layer[Throwable, SyncServiceClient] =
    SyncServiceClient.live(
      ZManagedChannel(ManagedChannelBuilder.forAddress("localhost", SyncServer.GRPCServerPort).usePlaintext()),
      options = CallOptions.DEFAULT,
      metadata = SafeMetadata.make(("user-id", userId.toString)),
    )

  val client1 = SyncServiceClient.bidirectionalStream(stream.filter(_._1 == 1).map(_._2)).provideLayer(clientLayer(1))
    //    .debug("client1")
    //    .runForeach {
    //        syncResponse => ZIO.log(s"Client 1 Response: ${syncResponse.toString}")
    //      }
    .tap(syncResponse => ZIO.succeed(client1Responses.addOne(syncResponse)))
    .onError {
      ex => ZIO.log(s"Client 1 Error: ${ex.toString}")
    }
  val client2 = SyncServiceClient.bidirectionalStream(stream.filter(_._1 == 2).map(_._2)).provideLayer(clientLayer(2))
    //    .runForeach {
    //        syncResponse => ZIO.log(s"Client 2 Response: ${syncResponse.toString}")
    //      }
    .tap(syncResponse => ZIO.succeed(client2Responses.addOne(syncResponse)))
    .onError {
      ex => ZIO.log(s"Client 2 Error: ${ex.toString}")
    }
  val client3 = SyncServiceClient.bidirectionalStream(stream.filter(_._1 == 3).map(_._2)).provideLayer(clientLayer(3))
    //    .debug("client3")
    //    .runForeach {
    //        syncResponse => ZIO.log(s"Client 3 Response: ${syncResponse.toString}")
    //      }
    .tap(syncResponse => ZIO.succeed(client3Responses.addOne(syncResponse)))
    .onError {
      ex => ZIO.log(s"Client 3 Error: ${ex.toString}")
    }

  //    }

  val output: Stream[Throwable, SyncResponse] = client1.merge(client2).merge(client3)
}