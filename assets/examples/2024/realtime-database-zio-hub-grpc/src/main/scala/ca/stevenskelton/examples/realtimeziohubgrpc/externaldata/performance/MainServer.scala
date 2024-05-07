package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata.performance

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser
import ca.stevenskelton.examples.realtimeziohubgrpc.externaldata.ZSyncServiceImpl
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{Clock, ExitCode, Schedule, URIO, ZIOAppDefault, ZLayer, durationInt}

object MainServer extends ZIOAppDefault:

  val GRPCServerPort: Int = 9000
  
  override def run: URIO[Any, ExitCode] =
    val app = for
      zSyncServiceImpl <- ZSyncServiceImpl.launch(hubCapacity = 6000)
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(zSyncServiceImpl.transformContextZIO(AuthenticatedUser.context)),
        )
        .launch
    yield
      grpcServer

    app.provideLayer(ClockExternalDataLayer.live(Clock.ClockLive, Schedule.fixed(1.second))).forever.exitCode