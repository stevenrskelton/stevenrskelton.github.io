---
title: "File Transfers using gRPC and ZIO"
categories:
  - Scala
tags:
  - ZIO
  - gRPC
excerpt_separator: <!--more-->
example: grpc-file-transfer-zio
sources:
  - "/src/main/scala/ca/stevenskelton/examples/grpcfiletransferzio/FileServiceImpl.scala"
---

gRPC with Protobuf is a framework to efficiently simplify the client-server networking requirements of modern
applications. One use-case where the low-level simplicity of pure HTTP maintains an advantage over gRPC is handling file
transfers: the uploading and downloading of contiguous binary block data. But gRPC can efficiently replicate all HTTP
functionality within its Protobuf message framework making it unnecessary to host separate gRPC and HTTP servers for
applications.<!--more-->

{% include multi_part_post.html %}

# Comparison of gRPC to HTTP/2

Because gRPC is built directly on top of HTTP/2 it is understandable that for simple file transfers gRPC can be viewed
as HTTP/2 with unnecessary overhead. For certain simple tasks, gRPC can never reach the resource efficiency of a pure
HTTP implementation.

For this reason, use-cases with significant volume or large file transfers will see noticeably reduced server resource
demands using HTTP instead of gRPC. However, this comes with the burden of maintaining another server cluster for HTTP,
or sideloading an HTTP server onto the gRPC server. The HTTP performance gain becomes a trade-off against system 
complexity.

Most gRPC overhead comes from the intentional copying of in-memory data models. The Java gRPC implementation will 
recopy userspace data to yet another array simply as a precaution: ensuring no code references and data immutability. 
This to enable assertions about internal state in order to optimize serialization code paths.

On the other hand, HTTP servers can be optimized for their simpler code paths without extra work; reading and writing 
data directly from storage to network with zero or minimal memory buffering and CPU processing.

# Protobuf Definition

## Feature Requirements

The primary requirements of a file transfer mechanism are:

- No additional encoding overhead
- Ability to determine progress

Other feature concerns related to client and server implementation will be discussed later, but compatibility should
still be verified when architecting the proto models. These features would include:

- the ability to handle partial-file resumption,
- multiplexed / concurrent segment transfers, and
- pre transfer actions, such as authentication, permissions, collision detection and quotas
- post actions, such as file renaming, name sanitation, or moving completed from temp to final directories.

The [gRPC stream](https://grpc.io/docs/what-is-grpc/core-concepts/#server-streaming-rpc) has the least amount of network
overhead for an indeterminate amount of data. Each gRPC message is an identical type, in our case we will define a
stream of `FileChunk` messages.

gRPC supports call [metadata](https://grpc.io/docs/guides/metadata/), which are directly like an HTTP header it is
possible to send additional data which would not be part of
each `FileChunk` in the stream. Perhaps, fields which do not vary for each message such as `filename` and `file_size`
would more efficiently be sent as call metadata, but this is typically

```protobuf
message FileChunk {
  //Name of file
  string filename = 1;
  //Total size of file
  uint64 file_size = 2;
  //Starting offset of current chunk
  uint64 offset = 3;
  //Binary data of chunk
  bytes body = 4;
}
```

## Server Definition

```protobuf
service FileService {
  rpc GetFile (GetFileRequest) returns (stream FileChunk);
  rpc SetFile (stream FileChunk) returns (SetFileResponse);
}
```

# Server Implementation

## Implementation Assumptions

Before getting into the server code, a set of assumptions have been made for simplicity.

First, the grpc connection is assumed to have been authenticated, and clients have full access to read/write to the
server's `filesDirectory` directory.  The value of `filesDirectory` might depend on the authenticated user of the 
client, with each separate user having access to only a home directory.

The sample `javaFile` function maps a `filename` request parameter to a server `java.io.File`, and implements no
sanitation on the value. Obviously allowing clients to enter `..` and `/` characters within the `filename` will
result in security vulnerabilities.  

```scala
private def javaFile(unsafeFilename: String): File = {
  File(s"$filesDirectory/$unsafeFilename")
}
```

Put requests where `filename` already exists will overwrite the existing file. Concurrent requests to read/write the
same file will result in corruption. How to solve this depends on requirements, but a common first recommended change 
would be to write to a temporary server directory and move completed files to a readable directory only after the upload
has completed successfully.

## Client GetFile (Download)

A `GetFileRequest` client request will result in the server streaming `filename` back to the client in a chunk size 
set by the server. The request could easily be expanded with an _offset_ field to allow partial file resumption, or
both _offset_ and _end_offset_ fields to allow concurrent download streams.

```protobuf
message GetFileRequest {
  string filename = 1;
}
```

The ZIO server implementation for the generated Protobuf is:

```scala
def getFile(request: GetFileRequest): Stream[StatusException, FileChunk]
```

The body is fairly simple after creating 2 private helper functions around the [ZIO NIO](https://zio.github.io/zio-nio/)
file library.  

One function to return the `file_size` of a file using ZIO _Files.size_.  

```scala
private def readFileSize(file: File): IO[IOException, Long] = {
  val path = Path.fromJava(file.toPath)
  Files.exists(path)
    .filterOrFail(_ == true)(FileNotFoundException(file.getName))
    .flatMap(_ => Files.size(path))
}
```

And another to create a read stream of the file using `ZStream.fromPath`, and luckily ZIO will chunk the stream to a 
specified `chunkSize`.  

```scala
private def readFile(file: File): UStream[ByteString] = {
  ZStream.fromPath(file.toPath, chunkSize = chunkSize)
    .chunks.map(chunk => ByteString.copyFrom(chunk.toArray))
    .catchAll { 
      ex => 
        ZStream.fromZIO {
          ZIO.logErrorCause(s"Error reading file ${file.getName}", Cause.fail(ex))
        }.drain
    }
}
```

The only logic left will be to convert the ZIO NIO file stream to a `FileChunk` stream. The only complexity here is
that each chunk will depend on the previous chunk.  The `offset` will simply be a running total of the body size of all
previous `FileChunk`.  ZIO `mapAccum` implements a stateful stream mapping, the state being the count of `sentBytes`.

```scala
override def getFile(request: GetFileRequest): Stream[StatusException, FileChunk] = {
  val file = javaFile(request.filename)
  ZStream.fromZIO(readFileSize(file))
    .flatMap { fileSize =>
      readFile(file).mapAccum(0L)((sentBytes, byteString) {
        val fileChunk = FileChunk.of(
          filename = file.getName,
          fileSize = fileSize,
          offset = sentBytes,
          body = byteString,
        )
        (sentBytes + byteString.size, fileChunk)
      })
    }
    .catchAll { ex =>
      ZStream.fromZIO(ZIO.fail(StatusException(io.grpc.Status.fromThrowable(ex))))
    }
}
```

There are many types of IO related errors which can happen accessing local files, our implementation will opt to return 
the default GRPC error status on all failures.

## Client SetFile (Upload)

As mentioned at the head of the article, GRPC allows metadata content to be part of call headers. Storing upload 
parameters as headers would simplify a streaming approach by removing the requirement to inspect the head element of 
the stream for parameters such as the `filename` and `file_size`.  

### Alternative GRPC Metadata Headers Approach

Standard GRPC practices are to use the call metadata to store call agnostic data, such as authentication, tracing, and 
other information which will apply to all calls. The `.proto` file specification doesn't include the ability to define
call message headers, so associating headers with calls will make `.proto` files an incomplete documentation of the
call.

Moreover, GRPC services are generated code creating an inflexibility to specifying individual call signatures. Each 
signature within a service will contain the same additional parameter. So a modified `setFile` will need to choose 
between having a generically typed header field or residing in a separate service class.

### ZIO Approach

The `setFile` request has the Scala signature:

```scala
def setFile(request: Stream[StatusException, FileChunk]): IO[StatusException, SetFileResponse]
```

The `SetFileResponse` response returns a `filename`, useful in the case where the input `filename` has been modified, 
such as stripping out illegal characters, adding a version identifier or translating to a UUID or URI. Our 
implementation will mirror the input. 

```protobuf
message SetFileResponse {
  string filename = 1;
}
```

The implementation will use a helper class, as the stream is stateful.  The head `FileChunk` of the stream will create
a new state by opening an `AsynchronousFileChannel` on the server that subsequent stream elements will append to. The 
`SaveFileAccum` state will also continue to update its `offset` field, which while unnecessary to function it will 
continue to be verified against the `offset` of the incoming stream elements. 

```scala
case class SaveFileAccum(
  asynchronousFileChannel: AsynchronousFileChannel,
  file: File,
  totalSize: Long,
  offset: Long,
)
```

The code for the `setFile` function will be center around creating a [ZSink](https://zio.dev/reference/stream/zsink/), 
a ZIO Stream class that processes a stream and returns a final output value.  Our sink will have the signature:

```scala
ZSink[Scope, StatusException | IOException, FileChunk, Nothing, Option[SaveFileAccum]]
``` 

The type parameters can be a bit intimidating at first, but are straight forward. Our `ZSink` needs a `Scope` to run 
in because it contains an open `AsynchronousFileChannel` which will need to be closed. It will throw both 
`StatusException` and `IOException`, but we could reduce this to just GRPC `StatusException` if made our sink a little
more complicated by handling all IO errors internally. It processes a stream of `FileChunk` items and will process all 
of them so it will have `Nothing` remaining elements, and its output will be an `Option[SaveFileAccum]`.

#### ZSink Output

The choice to return an `Option[SaveFileAccum]` allows us to externalize more code from the sink than if it returned
a `SetFileResponse` directly.

After the sink runs, the value of `Option[SaveFileAccum]` is either:
- If `Some` then a file was created. Either:
  - The client's `filesize` matches our file size means the upload was successful, or
  - The file size doesn't match, meaning the upload was incomplete.
- If `None`, then no file was created.


The ZIO code for this is:

```scala
ZIO.scoped {
  request.run(sink).flatMap {
    case Some(SaveFileAccum(_, file, expectedSize, actualSize)) if expectedSize == actualSize =>
      val response = SetFileResponse.of(file.getName)
      ZIO.succeed(response)

    case Some(SaveFileAccum(_, file, expectedSize, actualSize)) =>
      ZIO.logError(s"Upload ended at $actualSize of $expectedSize") *> ZIO.fail(StatusException(CANCELLED))

    case _ =>
      ZIO.logError("Could not create file") *> ZIO.fail(StatusException(UNKNOWN))
  }
}
```

#### ZSink Processing

The processing done by the sink to the stream of `FileChunk` has a few responsibilities. First, it will need to inspect
the first element of the stream to look for the upload parameters. The upload parameters will include the `filename` 
which will be used to create the `AsynchronousFileChannel`, and an expected `filesize`, which will be used to verify a
successfully completed stream. (Additionally we could include an MD5 or other hash to verify content as well as 
file size.)

Our code will include a verification that the expected upload file size is below a maximum, as we don't have unlimited
storage resources. Moreover, the processing of each `FileChunk` will continue to verify that the running total of bytes 
uploaded is below our server maximum and equal to the expected file size.

The sink will process the stream using a `foldLeftZIO` since we will need to maintain a state (`Option[SaveFileAccum]`)
updating it during each chunk processing.
```scala
ZSink.foldLeftZIO(None)((saveFileAccum, fileChunk) {
  //case 1: file channel not open => open file channel and write first chunk
  //case 2: file channel open, but chunk offset != expected throw INVALID_ARGUMENT
  //case 3: file channel open, but chunk greater than remaining bytes
  //case 4: channel open, chunk valid => append to file channel
})
```
##### Case 1: File Channel Not Open

This should only run on the first chunk. It will either create an open `AsynchronousFileChannel` or determine that the
file upload is invalid and throw an exception.

```scala
val file = javaFile(fileChunk.filename)
val path = Path.fromJava(file.toPath)
val chunk = Chunk.from(fileChunk.body.asScala.map(Byte.unbox))
if (fileChunk.fileSize > maxFileSize) {
  ZIO.logError(s"File too large, attempted ${fileChunk.fileSize} bytes")
    *> ZIO.fail(StatusException(OUT_OF_RANGE))
} else if (fileChunk.fileSize < chunk.length) {
  ZIO.logError(s"Invalid chunk ${chunk.length} exceeds total size ${fileChunk.fileSize}")
    *> ZIO.fail(StatusException(INVALID_ARGUMENT))
} else {
  for {
    _ <- ZIO.log(s"Uploading file ${path.toString}, size ${fileChunk.fileSize}")
    channel <- AsynchronousFileChannel.open(
        path,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.CREATE
      ) 
    _ <- channel.writeChunk(chunk, 0)
  } yield {
    Some(SaveFileAccum(channel, file, fileChunk.fileSize, chunk.length))
  }
}
```

##### Case 2: File Channel Open But Invalid Offset 

Verifies that this `FileChunk` is at the expected offset.

```scala
case Some(SaveFileAccum(_, _, _, offset)) if fileChunk.offset != offset =>
  ZIO.logError(s"Invalid chunk offset ${fileChunk.offset}, expected $offset")
    *> ZIO.fail(StatusException(INVALID_ARGUMENT))
```

##### Case 3: File Channel Open, But Chunk Greater Than Remaining Bytes

Verifies that this `FileChunk` body doesn't exceed the remaining number of bytes left in the stored file.

```scala
case Some(SaveFileAccum(_, _, totalSize, offset)) if fileChunk.body.size() > totalSize - offset =>
  ZIO.logError(s"Invalid chunk ${fileChunk.offset} exceeds total size $totalSize")
    *> ZIO.fail(StatusException(OUT_OF_RANGE))
```

##### Case 4: File Channel Open, Write to Channel

All invalid `FileChunk` cases have been checked, the `body` should be appended to the open file channel and the sink
state updated with the new expected offset for the next element of the stream.

```scala
val chunk = Chunk.from(fileChunk.body.asScala.map(Byte.unbox))
val saveFileAccum = SaveFileAccum(
  asynchronousFileChannel = asyncFileChannel,
  file = file,
  totalSize = totalSize,
  offset = offset + chunk.length,
)
asyncFileChannel.writeChunk(chunk, offset).as(Some(saveFileAccum))
```

# Conclusion

The next post in this series will create a Flutter client for this server component.