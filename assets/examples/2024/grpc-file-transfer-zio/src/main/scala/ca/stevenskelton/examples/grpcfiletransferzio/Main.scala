package ca.stevenskelton.examples.grpcfiletransferzio

import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{ExitCode, Schedule, URIO, ZIOAppDefault, ZLayer}

object Main extends ZIOAppDefault:

  private val GRPCServerPort = 9000

  override def run: URIO[Any, ExitCode] =
    val app = for
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(FileServiceImpl()),
        )
        .launch
    yield
      grpcServer

    app.forever.exitCode