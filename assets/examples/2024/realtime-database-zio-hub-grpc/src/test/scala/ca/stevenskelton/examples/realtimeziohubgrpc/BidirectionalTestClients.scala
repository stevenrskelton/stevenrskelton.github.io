package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser.UserId
import ca.stevenskelton.examples.realtimeziohubgrpc.SyncServer.HubCapacity
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{SyncRequest, SyncResponse}
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ServerLayer, ServiceList}
import zio.stream.{Take, ZStream}
import zio.{Dequeue, Fiber, Hub, Ref, Scope, UIO, ZIO}

import java.net.ServerSocket
import scala.collection.mutable
import scala.util.Using


object BidirectionalTestClients {
  def create(): ZIO[Scope, Nothing, BidirectionalTestClients] =
    for
      serverPort <- ZIO.succeed(Using(new ServerSocket(0))(_.getLocalPort).get)
      hub <- Hub.sliding[DataRecord](HubCapacity)
      database <- Ref.make[mutable.Map[Int, DataRecord]](mutable.Map.empty)
      zSyncService = ZSyncServiceImpl(hub, database)
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(serverPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(zSyncService.transformContextZIO(SyncServer.authenticatedUserContext)),
        )
        .launch.forkScoped
      client1 <- GrpcClient.build(1, serverPort)
      client2 <- GrpcClient.build(2, serverPort)
      client3 <- GrpcClient.build(3, serverPort)
      responses <- client1.responses.merge(client2.responses).merge(client3.responses).toQueueUnbounded
    yield new BidirectionalTestClients(
      grpcServer,
      zSyncService,
      responses,
      client1,
      client2,
      client3,
    )
}

case class BidirectionalTestClients(
                                     grpcServer: Fiber.Runtime[Throwable, ?],
                                     zSyncServiceImpl: ZSyncServiceImpl,
                                     responses: Dequeue[Take[Throwable, (UserId, SyncResponse)]],
                                     client1: GrpcClient,
                                     client2: GrpcClient,
                                     client3: GrpcClient,
                                   ):

  def responses(
                 count: Int,
                 requests: Seq[(UserId, SyncRequest)],
               ): UIO[Seq[(UserId, SyncResponse)]] =
    ZIO.collectAll:
      requests.map:
        case (1, syncRequest) => client1.requests.offer(syncRequest)
        case (2, syncRequest) => client2.requests.offer(syncRequest)
        case (3, syncRequest) => client3.requests.offer(syncRequest)
    *> pullNresponses(count)

  private def pullNresponses(i: Int): UIO[Seq[(UserId, SyncResponse)]] =
    responses.takeBetween(i, Int.MaxValue).flatMap:
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