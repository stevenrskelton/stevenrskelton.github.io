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

Because gRPC is built directly on top of HTTP/2 it is understandable that for simple file transfers gRPC is HTTP/2 
with overhead, meaning gRPC can never reach the resource efficiency of a pure HTTP server for certain tasks.  

Use-cases with significant file transfers should prefer a stock HTTP implementation over gRPC. This may change in the 
future via additional development of the gRPC libraries, however it will likely never be a priority. 

Typically, gRPC intentionally creates overhead by copying data models multiple times in memory for various reasons. The 
Java gRPC implementation will copy all array data to a second array to ensure immutability, thereby allowing assertions 
about internal state to be made leading to optimized serialization code paths.

On the other hand, HTTP servers typically optimize for low-level code paths by directly reading/writing data from 
storage to network with zero or minimal memory buffering or CPU processing.

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

