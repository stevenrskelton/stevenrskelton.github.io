package ca.stevenskelton.examples.grpcfiletransferzio

import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ServerLayer, ServiceList}
import zio.{ExitCode, URIO, ZIOAppDefault}

import java.io.File

object Main extends ZIOAppDefault:

  private val GRPCServerPort = 9000
  private val FilesDirectory = File("files")
  private val ChunkSize = 65_536
  private val MaxFileSize = 250 * 1_048_576L
  
  override def run: URIO[Any, ExitCode] =
    FilesDirectory.mkdir()
    val app = for
      grpcServer <- ServerLayer
        .fromServiceList(
          ServerBuilder.forPort(GRPCServerPort).addService(ProtoReflectionService.newInstance()),
          ServiceList.add(FileServiceImpl(FilesDirectory, ChunkSize, MaxFileSize)),
        )
        .launch
    yield
      grpcServer

    app.forever.exitCode