package ca.stevenskelton.examples.grpcfiletransferzio

import ca.stevenskelton.examples.grpcfiletransferzio.file_service.{GetFileRequest, FileChunk, SetFileResponse}
import ca.stevenskelton.examples.grpcfiletransferzio.file_service.ZioFileService.FileService
import io.grpc.StatusException
import zio.{IO, stream}

import com.google.protobuf.ByteString
import io.grpc.StatusException
import zio.nio.channels.*
import zio.nio.file.*
import zio.stream.{Stream, UStream, ZSink, ZStream}
import zio.{Cause, Chunk, IO, Scope, ZIO}

import java.io.{File, IOException}
import java.nio.file.StandardOpenOption
import scala.jdk.CollectionConverters.*

class FileServiceImpl(filesDirectory: File, chunkSize: Int) extends FileService:

  private case class SaveFileAccum(asynchronousFileChannel: AsynchronousFileChannel, file: File, totalSize: Long, offset: Long)

  private def javaFile(unsafeFilename: String): File = 
    new File(s"${filesDirectory.getPath}/$unsafeFilename")

  override def getFile(request: GetFileRequest): Stream[StatusException, FileChunk] = {
    val file = javaFile(request.filename)
    ZStream.fromZIO(readFileSize(file)).flatMap {
      fileSize =>
        readFile(file).mapAccum(0)((sentBytes, byteString) =>
          val fileChunk = FileChunk.of(
            filename = file.getName,
            size = fileSize,
            offset = sentBytes,
            success = sentBytes + byteString.size == fileSize,
            error = null,
            body = byteString,
          )
          (sentBytes + byteString.size, fileChunk)
        )
      }
      .catchAll {
        ex => ZStream.fromZIO(ZIO.logCause("updateUserSocial", Cause.fail(ex)).flatMap(_ => ZIO.fail(StatusException(io.grpc.Status.fromThrowable(ex)))))
      }
  }

  override def setFile(request: Stream[StatusException, FileChunk]): IO[StatusException, SetFileResponse] = {
    val sink = ZSink.foldLeftZIO[Scope, StatusException | IOException, FileChunk, Option[SaveFileAccum]](None)((saveFileAccum, fileChunk) =>
      saveFileAccum match {
        case Some(SaveFileAccum(asyncFileChannel, file, totalSize, offset)) =>
          if (fileChunk.offset != offset) {
            ZIO.fail(IOException("Invalid chunk offset"))
          } else {
            val chunk = Chunk.from(fileChunk.body.asScala.map(Byte.unbox))
            val saveFileAccum = SaveFileAccum(
              asynchronousFileChannel = asyncFileChannel,
              file = file,
              totalSize = totalSize,
              offset = offset + chunk.length,
            )
            asyncFileChannel.writeChunk(chunk, offset).as(Some(saveFileAccum))
          }
        case None =>
          val file = javaFile(fileChunk.filename)
          val path = Path.fromJava(file.toPath)
          if (fileChunk.size == 0) {
            Files.deleteIfExists(path).as(Some(SaveFileAccum(null, file, 0, 0)))
          } else {
            AsynchronousFileChannel.open(
              path,
              StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE
            ).flatMap {
              channel =>
                val chunk = Chunk.from(fileChunk.body.asScala.map(Byte.unbox))
                val saveFileAccum = SaveFileAccum(channel, file, fileChunk.size, chunk.length)
                channel.writeChunk(chunk, 0).as(Some(saveFileAccum))
            }
          }
      }
    )
    val z = ZIO.scoped {
      request.run(sink).flatMap {
        case Some(SaveFileAccum(_, file, expectedSize, actualSize)) if expectedSize == actualSize =>
          val response = SetFileResponse.of(file.getName)
          ZIO.succeed(response)
        case _ =>
          ZIO.fail(StatusException(io.grpc.Status.NOT_FOUND))
      }
    }
    z.catchAll:
      ex => ZIO.logCause("setFile", Cause.fail(ex)).flatMap(_ => ZIO.fail(StatusException(io.grpc.Status.fromThrowable(ex))))
  }

  private def readFileSize(file: File): IO[IOException, Long] =
    val path = Path.fromJava(file.toPath)
    zio.nio.file.Files.exists(path)
      .filterOrFail(_ == true)(java.io.FileNotFoundException(file.getName))
      .flatMap:
        _ => zio.nio.file.Files.size(path)

  private def readFile(file: File): UStream[ByteString] =
    ZStream.fromPath(file.toPath, chunkSize = chunkSize).chunks
      .map:
        chunk => ByteString.copyFrom(chunk.toArray)
      .catchAll:
        ex => ZStream.fromZIO(ZIO.logErrorCause(s"Error reading file ${file.getName}", Cause.fail(ex))).drain
