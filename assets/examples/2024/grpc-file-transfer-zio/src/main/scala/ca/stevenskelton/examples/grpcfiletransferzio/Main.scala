package ca.stevenskelton.examples.grpcfiletransferzio

import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{RequestContext, ServerLayer, ServiceList}
import zio.{ExitCode, Schedule, URIO, ZIOAppDefault, ZLayer}

import java.io.File

object Main extends ZIOAppDefault:

  private val GRPCServerPort = 9000
  private val FilesDirectory = new File("files")
  private val ChunkSize = 65536
  
  override def run: URIO[Any, ExitCode] =
    FilesDirectory.mkdir()
    val app = for
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(FileServiceImpl(FilesDirectory, ChunkSize)),
        )
        .launch
    yield
      grpcServer

    app.forever.exitCode