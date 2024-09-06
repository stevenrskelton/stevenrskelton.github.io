package ca.stevenskelton.examples.grpcfiletransferzio

import ca.stevenskelton.examples.grpcfiletransferzio.file_service.ZioFileService.FileService
import ca.stevenskelton.examples.grpcfiletransferzio.file_service.{FileChunk, GetFileRequest, SetFileResponse}
import com.google.protobuf.ByteString
import io.grpc.StatusException
import zio.nio.channels.*
import zio.nio.file.*
import zio.stream.{Stream, UStream, ZSink, ZStream}
import zio.{Cause, Chunk, IO, Scope, ZIO, stream}
import zio.test.*

import java.io.File
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.util.UUID

object FileServiceImplSpec extends ZIOSpecDefault:

  override def spec: Spec[Any, Any] = suite("FileServiceImpSpec")(
    test("set then get") {

      val filename = UUID.randomUUID.toString

      val chunkSize = 10
      val validFileChunks = 0 until 5 map :
        i =>
          FileChunk.of(
            filename = filename,
            size = 5 * chunkSize,
            offset = i * chunkSize,
            success = i == 4,
            body = createCountingByteString(i, chunkSize),
          )

      for
        tempDirectory <- Files.createTempDirectory(Some("grpcfiletransferzio"), Nil)
        fileService = FileServiceImpl(tempDirectory.toFile, chunkSize = 16, maxFileSize = Byte.MaxValue)
        setFileResponse <- fileService.setFile(ZStream.fromIterable(validFileChunks))
        savedFileChunk <- fileService.getFile(GetFileRequest.of(filename)).runCollect
      yield assertTrue(
        setFileResponse.filename == filename,
        savedFileChunk.size == 4,
        savedFileChunk.last.filename == filename,
        savedFileChunk.last.size == 5 * chunkSize,
        savedFileChunk.last.offset == 3 * 16,
        savedFileChunk.last.success,
        savedFileChunk.last.body.size == (5 * chunkSize) - (3 * 16),
        hasCountingLength(savedFileChunk.map(_.body)) == 5 * chunkSize
      )
    })

  def createCountingByteString(chunk: Int, chunkLength: Int): ByteString =
    val start = chunk * chunkLength
    ByteString.copyFrom((0 until chunkLength).map(i => (start + i).toByte).toArray)

  def hasCountingLength(byteStrings: Seq[ByteString]): Int =
    val full = byteStrings.foldLeft(ByteString.EMPTY)(_.concat(_)).iterator
    var i = 0
    while full.hasNext do
      if full.next.toInt != i then return -1
      else i += 1
    i
