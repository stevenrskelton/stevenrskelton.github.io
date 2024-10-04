---
title: "Flutter gRPC File Transfers"
categories:
  - Dart
tags:
  - Bloc
  - gRPC
excerpt_separator: <!--more-->
sources:
  - "/lib/file_transfer_change_notifier.dart"
  - "/lib/file_transfer_grpc_client.dart"
  - "/lib/file_transfer_progress.dart"
  - "/lib/file_transfer_progress_bar_widget.dart"
  - "/lib/file_transfer_widget.dart"
example: flutter-grpc-file-transfer
---
Modern mobile apps are benefiting from using gRPC with Protobuf to reduce boilerplate code for their client-server 
networking implementation. While directly implemented by gRPC, the library can easily implement all necessary features
for efficient file transfers.<!--more-->

{% include multi_part_post.html %}

# gRPC versus JavaScript libraries

Cloud providers have libraries with support for uploading and downloading from cloud storage. These libraries approach
transfers as a large number of HTTP requests, breaking up large files into smaller transfer requests. As requests move
from pending, in progress, to complete, the current status of the transfer can be calculated.

The gRPC approach is similar, however instead of using separate HTTP requests each file chunk is sequentially sent 
over an HTTP/2 stream. The performance of both approaches is comparable, as multiple requests are multiplexed over a 
single HTTP connection closely mirroring how HTTP/2 streaming operates over a single connection. The only real 
difference is encountered when moving from sequential to concurrent operation, ie: sending multiple chunks at a time. 
Request multiplexing directly support this, however multiple stream requests need to be used to implement this using 
gRPC as each stream is inherently sequential and ordered.

# pubspec.yaml Dependencies

This sample client requires 2 Flutter libraries:
- [grpc](https://pub.dev/packages/grpc) core implementation for networking.
- [image_picker](https://pub.dev/packages/image_picker) implements an image picker widget to easily select upload files.

# Flutter UI Flow

[MACS J0417.5-1154 Wide Field](https://webbtelescope.org/contents/media/images/2024/128/01J6CXCDNSGF87TZEX379WHDXB)
Full Res (For Display), 4623 X 4623, PNG (35.14 MB)

{%
include figure image_path="/assets/images/2024/10/01_select_upload_image.jpg"
caption=""
img_style="padding: 0 20px 10px 20px; background-color: white; height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/02_image_picker.jpg"
caption=""
img_style="padding: 0 20px 10px 20px; background-color: white; height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/03_upload_progress.jpg"
caption=""
img_style="padding: 0 20px 10px 20px; background-color: white; height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/04_upload_complete.jpg"
caption=""
img_style="padding: 0 20px 10px 20px; background-color: white; height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/05_download_progress.jpg"
caption=""
img_style="padding: 0 20px 10px 20px; background-color: white; height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/06_download_complete.jpg"
caption=""
img_style="padding: 0 20px 10px 20px; background-color: white; height: 450px;"
%}

# Classes

## file_transfer_change_notifier.dart

## file_transfer_grpc_client.dart

## file_transfer_progress.dart

## file_transfer_progress_bar_widget.dart

## file_transfer_widget.dart