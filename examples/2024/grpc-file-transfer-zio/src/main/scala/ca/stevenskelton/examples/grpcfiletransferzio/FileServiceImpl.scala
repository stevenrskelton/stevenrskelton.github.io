package ca.stevenskelton.examples.grpcfiletransferzio

import ca.stevenskelton.examples.grpcfiletransferzio.file_service.ZioFileService.FileService
import ca.stevenskelton.examples.grpcfiletransferzio.file_service.{FileChunk, GetFileRequest, SetFileResponse}
import com.google.protobuf.ByteString
import io.grpc.StatusException
import io.grpc.Status.*
import zio.nio.channels.*
import zio.nio.file.*
import zio.stream.{Stream, UStream, ZSink, ZStream}
import zio.{Cause, Chunk, IO, Scope, ZIO, stream}

import java.io.{File, FileNotFoundException, IOException}
import java.nio.file.StandardOpenOption
import scala.jdk.CollectionConverters.*

class FileServiceImpl(filesDirectory: File, chunkSize: Int, maxFileSize: Long) extends FileService:

  private def javaFile(unsafeFilename: String): File = File(s"${filesDirectory.getPath}/$unsafeFilename")

  override def getFile(request: GetFileRequest): Stream[StatusException, FileChunk] =
    val file = javaFile(request.filename)
    ZStream.fromZIO(readFileSize(file))
      .flatMap: fileSize =>
        readFile(file).mapAccum(0L)((sentBytes, byteString) =>
          val fileChunk = FileChunk.of(
            filename = file.getName,
            fileSize = fileSize,
            offset = sentBytes,
            success = sentBytes + byteString.size == fileSize,
            body = byteString,
          )
          (sentBytes + byteString.size, fileChunk)
        )
      .catchAll: ex =>
        ZStream.fromZIO(ZIO.logCause("updateUserSocial", Cause.fail(ex)).flatMap(_ => ZIO.fail(StatusException(io.grpc.Status.fromThrowable(ex)))))
  end getFile

  override def setFile(request: Stream[StatusException, FileChunk]): IO[StatusException, SetFileResponse] =

    case class SaveFileAccum(
                              asynchronousFileChannel: AsynchronousFileChannel,
                              file: File,
                              totalSize: Long,
                              offset: Long,
                            )

    val sink = ZSink.foldLeftZIO[Scope, StatusException | IOException, FileChunk, Option[SaveFileAccum]](None)((saveFileAccum, fileChunk) =>
      saveFileAccum match

        case Some(SaveFileAccum(_, _, _, offset)) if fileChunk.offset != offset =>
          ZIO.logError(s"Invalid chunk offset ${fileChunk.offset}, expected $offset")
            *> ZIO.fail(StatusException(INVALID_ARGUMENT))

        case Some(SaveFileAccum(asyncFileChannel, file, totalSize, offset)) =>
          val chunk = Chunk.from(fileChunk.body.asScala.map(Byte.unbox))
          val saveFileAccum = SaveFileAccum(
            asynchronousFileChannel = asyncFileChannel,
            file = file,
            totalSize = totalSize,
            offset = offset + chunk.length,
          )
          if saveFileAccum.offset > totalSize then
            ZIO.logError(s"Invalid chunk ${fileChunk.offset} exceeds total size $totalSize")
              *> ZIO.fail(StatusException(OUT_OF_RANGE))
          else
            asyncFileChannel.writeChunk(chunk, offset).as(Some(saveFileAccum))

        case None =>
          val file = javaFile(fileChunk.filename)
          val path = Path.fromJava(file.toPath)
          val chunk = Chunk.from(fileChunk.body.asScala.map(Byte.unbox))
          if fileChunk.fileSize > maxFileSize then
            ZIO.logError(s"File too large, attempted ${fileChunk.fileSize} bytes")
              *> ZIO.fail(StatusException(OUT_OF_RANGE))
          else if fileChunk.fileSize < chunk.length then
            ZIO.logError(s"Invalid chunk ${chunk.length} exceeds total size ${fileChunk.fileSize}")
              *> ZIO.fail(StatusException(INVALID_ARGUMENT))
          else
            for
              _ <- ZIO.log(s"Uploading file ${path.toString}, size ${fileChunk.fileSize}")
              channel <- AsynchronousFileChannel.open(
                path,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE
              )
              _ <- channel.writeChunk(chunk, 0)
            yield
              Some(SaveFileAccum(channel, file, fileChunk.fileSize, chunk.length))
    )
    val z = ZIO.scoped:
      request.run(sink).flatMap:

        case Some(SaveFileAccum(_, file, expectedSize, actualSize)) if expectedSize == actualSize =>
          val response = SetFileResponse.of(file.getName)
          ZIO.succeed(response)

        case Some(SaveFileAccum(_, file, expectedSize, actualSize)) =>
          ZIO.logError(s"Upload ended at $actualSize of $expectedSize") *> ZIO.fail(StatusException(CANCELLED))

        case _ =>
          ZIO.logError("Could not create file") *> ZIO.fail(StatusException(UNKNOWN))

    z.catchAll:
      case ex: StatusException => ZIO.fail(ex)
      case ex: IOException => ZIO.logErrorCause(Cause.fail(ex)) *> ZIO.fail(StatusException(RESOURCE_EXHAUSTED))

  end setFile

  private def readFileSize(file: File): IO[IOException, Long] =
    val path = Path.fromJava(file.toPath)
    Files.exists(path)
      .filterOrFail(_ == true)(FileNotFoundException(file.getName))
      .flatMap: _ =>
        Files.size(path)

  private def readFile(file: File): UStream[ByteString] =
    ZStream.fromPath(file.toPath, chunkSize = chunkSize)
      .chunks.map: chunk =>
        ByteString.copyFrom(chunk.toArray)
      .catchAll: ex =>
        ZStream.fromZIO(ZIO.logErrorCause(s"Error reading file ${file.getName}", Cause.fail(ex))).drain
