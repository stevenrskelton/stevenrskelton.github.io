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
transfers: the uploading and downloading of contiguous binary block data. gRPC is built directly on top of HTTP/2, so 
it is understandable that when advanced functionality isn't required it becomes an onerous addition. While gRPC 
represents HTTP/2 with overhead, meaning it can never have the resource efficiency of a pure HTTP server, gRPC can 
replicate all HTTP file transfer features within its Protobuf message framework making it unnecessary to host both gRPC 
and separate HTTP servers for many types of applications.
<!--more-->

Use-cases with significant file transfers should prefer a stock HTTP implementation over gRPC. This may change in the 
future via additional development to the gRPC libraries, however it is not a priority for current gRPC implementations.
Typically, gRPC will copy data models multiple times in memory for various reasons. The Java gRPC implementation will 
intentionally array copy data to a second array to ensure immutability, allowing assertions about internal state to be 
made to prioritize faster serialization.

An HTTP server maintains low-level code paths to directly read/write data from storage to network with zero or minimal 
memory buffering or CPU processing.

{% include multi_part_post.html %}

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
  //True if last chunk
  bool success = 4;
  //Binary data of chunk
  bytes body = 6;
}
```

## Server Definition

```protobuf
service FileService {
  rpc GetFile (GetFileRequest) returns (stream FileChunk);
  rpc SetFile (stream FileChunk) returns (SetFileResponse);
}

message GetFileRequest {
  //Resource identifier for client entity
  string filename = 1;
}

message SetFileResponse {
  //Resource identifier for server entity
  string filename = 1;
}
```

# Server Send (Upload)

# Server Receive (Download)

