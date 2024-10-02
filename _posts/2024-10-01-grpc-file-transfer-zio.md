---
title: "File Transfers using gRPC and ZIO"
categories:
  - Scala
tags:
  - ZIO
  - gRPC
excerpt_separator: <!--more-->
example: grpc-file-transfer-zio
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

For this reason, use-cases with significant or performance critical file transfers should prefer stock HTTP over 
gRPC. Unfortunately this may mean adding a second cluster of HTTP servers to your deployments, making the performance
gain a trade-off against system complexity. While gRPC is in active development, it is unlikely to ever optimize for 
such a specialized task considering HTTP is ultimately the better alternative. 

Most of the overhead of gRPC comes from intentionally copying in-memory data models. The Java gRPC implementation will 
copy array data to secondary arrays to break all code references and ensure immutability. This will allow assertions 
about internal state lending to a heavily optimized serialization code path.

On the other hand, HTTP servers have optimized code paths for reading/writing data directly from storage to network 
with zero or minimal memory buffering or CPU processing. Exactly what is wanted for a file transfer.

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

gRPC supports call [metadata](https://grpc.io/docs/guides/metadata/), which are directly  like an HTTP header it is possible to send additional data which would not be part of
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

## Client GetFile (Download)

```protobuf
message GetFileRequest {
  string filename = 1;
}
```

```scala
def getFile(request: GetFileRequest): Stream[StatusException, FileChunk]
```

```scala
 private def readFileSize(file: File): IO[IOException, Long] = {
  val path = Path.fromJava(file.toPath)
  Files.exists(path)
    .filterOrFail(_ == true)(FileNotFoundException(file.getName))
    .flatMap(_ => Files.size(path))
}
```

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

```scala
private def javaFile(unsafeFilename: String): File = {
  File(s"${filesDirectory.getPath}/$unsafeFilename")
}
```

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
        ZStream.fromZIO(ZIO.logCause("updateUserSocial", Cause.fail(ex)).flatMap(_ => ZIO.fail(StatusException(io.grpc.Status.fromThrowable(ex)))))
      }
  }
```

## Client SetFile (Upload)

```protobuf
message SetFileResponse {
  string filename = 1;
}
```

```scala
def setFile(request: Stream[StatusException, FileChunk]): IO[StatusException, SetFileResponse]
```

```scala
case class SaveFileAccum(
  asynchronousFileChannel: AsynchronousFileChannel,
  file: File,
  totalSize: Long,
  offset: Long,
)
```