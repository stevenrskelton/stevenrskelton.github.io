package ca.stevenskelton.examples.grpcfiletransferzio

import ca.stevenskelton.examples.grpcfiletransferzio.file_service.{FileChunk, GetFileRequest, SetFileResponse}
import com.google.protobuf.ByteString
import io.grpc.Status.*
import io.grpc.StatusException
import zio.nio.file.*
import zio.stream.ZStream
import zio.test.*
import zio.test.Assertion.*
import zio.{Exit, stream}

import java.util.UUID

object FileServiceImplSpec extends ZIOSpecDefault:

  private val chunk10b = 10
  private val chunk16b = 16

  private val tmpDirectory = Some("grpcfiletransferzio")

  override def spec: Spec[Any, Any] = suite("FileServiceImpSpec")(
    test("set 5*10b chunks then get 4*16b chunks") {

      val filename = UUID.randomUUID.toString

      val validFileChunks = 0 until 5 map :
        i =>
          FileChunk.of(
            filename = filename,
            fileSize = 5 * chunk10b,
            offset = i * chunk10b,
            body = createCountingByteString(i * chunk10b, chunk10b),
          )

      for
        tempDirectory <- Files.createTempDirectory(tmpDirectory, Nil)
        fileService = FileServiceImpl(tempDirectory.toFile, chunkSize = chunk16b, maxFileSize = Byte.MaxValue, maxChunkSize = Int.MaxValue)
        setFileResponse <- fileService.setFile(ZStream.fromIterable(validFileChunks))
        savedFileChunk <- fileService.getFile(GetFileRequest.of(filename)).runCollect
      yield assertTrue(
        setFileResponse.filename == filename,
        savedFileChunk.size == 4,
        savedFileChunk.last.filename == filename,
        savedFileChunk.last.fileSize == 5 * chunk10b,
        savedFileChunk.last.offset == 3 * chunk16b,
        savedFileChunk.last.body.size == (5 * chunk10b) - (3 * chunk16b),
        hasCountingLength(savedFileChunk.map(_.body)) == 5 * chunk10b
      )
    },

    test("set 3*16b chunks then get 5*10b chunks") {

      val filename = UUID.randomUUID.toString

      val validFileChunks = 0 until 3 map :
        i =>
          FileChunk.of(
            filename = filename,
            fileSize = 3 * chunk16b,
            offset = i * chunk16b,
            body = createCountingByteString(i * chunk16b, chunk16b),
          )

      for
        tempDirectory <- Files.createTempDirectory(tmpDirectory, Nil)
        fileService = FileServiceImpl(tempDirectory.toFile, chunkSize = chunk10b, maxFileSize = Byte.MaxValue, maxChunkSize = Int.MaxValue)
        setFileResponse <- fileService.setFile(ZStream.fromIterable(validFileChunks))
        savedFileChunk <- fileService.getFile(GetFileRequest.of(filename)).runCollect
      yield assertTrue(
        setFileResponse.filename == filename,
        savedFileChunk.size == 5,
        savedFileChunk.last.filename == filename,
        savedFileChunk.last.fileSize == 3 * chunk16b,
        savedFileChunk.last.offset == 4 * chunk10b,
        savedFileChunk.last.body.size == (3 * chunk16b) - (4 * chunk10b),
        hasCountingLength(savedFileChunk.map(_.body)) == 3 * chunk16b
      )
    },

    test("set 4b+5b+3b chunks then get 16b chunk") {
      val filename = UUID.randomUUID.toString

      val validFileChunks = Seq(
        FileChunk.of(
          filename = filename,
          fileSize = 4 + 5 + 3,
          offset = 0,
          body = createCountingByteString(0, 4),
        ),
        FileChunk.of(
          filename = filename,
          fileSize = 4 + 5 + 3,
          offset = 4,
          body = createCountingByteString(4, 5),
        ),
        FileChunk.of(
          filename = filename,
          fileSize = 4 + 5 + 3,
          offset = 4 + 5,
          body = createCountingByteString(9, 3),
        ),
      )

      for
        tempDirectory <- Files.createTempDirectory(tmpDirectory, Nil)
        fileService = FileServiceImpl(tempDirectory.toFile, chunkSize = chunk16b, maxFileSize = Byte.MaxValue, maxChunkSize = Int.MaxValue)
        setFileResponse <- fileService.setFile(ZStream.fromIterable(validFileChunks))
        savedFileChunk <- fileService.getFile(GetFileRequest.of(filename)).runCollect
      yield assertTrue(
        setFileResponse.filename == filename,
        savedFileChunk.size == 1,
        savedFileChunk.last.filename == filename,
        savedFileChunk.last.fileSize == 4 + 5 + 3,
        savedFileChunk.last.offset == 0,
        savedFileChunk.last.body.size == 4 + 5 + 3,
        hasCountingLength(savedFileChunk.map(_.body)) == 4 + 5 + 3
      )
    },

    test("fail if file is over maximum, well-formed") {

      val validFileChunk = FileChunk.of(
        filename = UUID.randomUUID.toString,
        fileSize = chunk16b,
        offset = 0,
        body = createCountingByteString(0, chunk16b),
      )

      for
        tempDirectory <- Files.createTempDirectory(tmpDirectory, Nil)
        fileService = FileServiceImpl(tempDirectory.toFile, chunkSize = chunk10b, maxFileSize = chunk10b, maxChunkSize = Int.MaxValue)
        exit <- fileService.setFile(ZStream.from(validFileChunk)).exit
      yield
        assertTrue(exit.is(_.failure).getStatus == OUT_OF_RANGE)
    },

    test("fail if file is over maximum, invalid size header") {

      val filename = UUID.randomUUID.toString

      val invalidFileChunks = Seq(
        FileChunk.of(
          filename = filename,
          fileSize = 8,
          offset = 0,
          body = createCountingByteString(0, 8),
        ),
        FileChunk.of(
          filename = filename,
          fileSize = 8,
          offset = 8,
          body = createCountingByteString(8, 8),
        ),
      )

      for
        tempDirectory <- Files.createTempDirectory(tmpDirectory, Nil)
        fileService = FileServiceImpl(tempDirectory.toFile, chunkSize = chunk10b, maxFileSize = chunk10b, maxChunkSize = Int.MaxValue)
        exit <- fileService.setFile(ZStream.fromIterable(invalidFileChunks)).exit
      yield
        assertTrue(exit.is(_.failure).getStatus == OUT_OF_RANGE)
    },
  )

  private def createCountingByteString(start: Int, chunkLength: Int): ByteString =
    ByteString.copyFrom((0 until chunkLength).map(i => (start + i).toByte).toArray)

  private def hasCountingLength(byteStrings: Seq[ByteString]): Int =
    val full = byteStrings.foldLeft(ByteString.EMPTY)(_.concat(_)).iterator
    var i = 0
    while full.hasNext do
      if full.next.toInt != i then return -1
      else i += 1
    i
