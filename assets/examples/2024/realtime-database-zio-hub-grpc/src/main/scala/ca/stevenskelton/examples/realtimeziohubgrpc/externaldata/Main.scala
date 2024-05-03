package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.AuthenticatedUser
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{ExitCode, URIO, ZIOAppDefault}

object Main extends ZIOAppDefault:

  private val GRPCServerPort = 9000

  override def run: URIO[Any, ExitCode] =
    val app = for
      zSyncServiceImpl <- ZSyncServiceImpl.launch()
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(zSyncServiceImpl.transformContextZIO(AuthenticatedUser.context)),
        )
        .launch
    yield
      grpcServer

    app.provideLayer(ExternalDataLayer.live).forever.exitCode