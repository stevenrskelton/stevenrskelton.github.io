package ca.stevenskelton.examples.grpcfiletransferzio

import scalapb.zio_grpc.{ServerMain, ServiceList}

import java.io.File

object Main extends ServerMain:

  override def port: Int = 50051

  private val FilesDirectory = File("files")
  private val ChunkSize = 65_536
  private val MaxFileSize = 250 * 1_048_576L
  private val MaxChunkSize = ChunkSize

  override def services: ServiceList[Any] = ServiceList.add(FileServiceImpl(FilesDirectory, ChunkSize, MaxFileSize, MaxChunkSize))