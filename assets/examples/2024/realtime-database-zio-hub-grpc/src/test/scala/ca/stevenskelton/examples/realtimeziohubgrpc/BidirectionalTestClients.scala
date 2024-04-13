package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse}
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ServerLayer, ServiceList}
import zio.stream.{Take, ZStream}
import zio.{Dequeue, Fiber, Scope, UIO, ZIO}

import java.net.ServerSocket
import scala.util.Using


object BidirectionalTestClients {
  def launch: ZIO[Scope, Nothing, BidirectionalTestClients] =
    for
      serverPort <- ZIO.succeed(Using(new ServerSocket(0))(_.getLocalPort).get)
      zSyncServiceImpl <- ZSyncServiceImpl.launch
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(serverPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(zSyncServiceImpl.transformContextZIO(SyncServer.authenticatedUserContext)),
        )
        .launch.forkScoped
      client1 <- GrpcClient.launch(1, serverPort)
      client2 <- GrpcClient.launch(2, serverPort)
      client3 <- GrpcClient.launch(3, serverPort)
      responses <- client1.responses.merge(client2.responses).merge(client3.responses).toQueueUnbounded
    yield new BidirectionalTestClients(
      grpcServer,
      zSyncServiceImpl,
      responses,
      client1,
      client2,
      client3,
    )
}

case class BidirectionalTestClients(
                                     grpcServer: Fiber.Runtime[Throwable, ?],
                                     zSyncServiceImpl: ZSyncServiceImpl,
                                     responseDequeue: Dequeue[Take[Throwable, (UserId, SyncResponse)]],
                                     client1: GrpcClient,
                                     client2: GrpcClient,
                                     client3: GrpcClient,
                                   ):

  def responses(
                 count: Int,
                 requests: (UserId, SyncRequest)*,
               ): UIO[Seq[(UserId, SyncResponse)]] =
    ZIO.collectAll:
      requests.map:
        case (1, syncRequest) => client1.requests.offer(syncRequest)
        case (2, syncRequest) => client2.requests.offer(syncRequest)
        case (3, syncRequest) => client3.requests.offer(syncRequest)
    *> pullNresponses(count)

//  def init(requests: Seq[(UserId, SyncRequest)]): UIO[Unit] = {
//    ZIO.collectAll {
//      requests.map {
//        case (1, syncRequest) => client1.requests.offer(syncRequest)
//        case (2, syncRequest) => client2.requests.offer(syncRequest)
//        case (3, syncRequest) => client3.requests.offer(syncRequest)
//      }
//    } *> responseDequeue.takeN(3).map {
//      chunk => if (chunk.nonEmpty) Take.dieMessage("Should be empty") else ()
//    }
//  }

  private def pullNresponses(i: Int): UIO[Seq[(UserId, SyncResponse)]] =
    responseDequeue.takeBetween(i, Int.MaxValue).flatMap:
      chunk =>
        ZIO.collectAll:
          chunk.map(_.done.catchAll {
            ex => ZIO.log(s"EX: ${ex.getClass} ${ex.toString}") *> ZIO.succeed(Nil)
          })
        .map(_.flatten)
        .flatMap:
          responseChunk =>
            if (responseChunk.size < i) pullNresponses(i - responseChunk.size).map(responseChunk ++ _)
            else ZIO.succeed(responseChunk)