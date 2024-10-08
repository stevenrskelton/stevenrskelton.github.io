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

{% include table-of-contents.html height="500px" %}

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

# Flutter Workflow

Adding the complexity of streaming file transfers only benefits larger file transfers. Small transfers that can fit into
the 4MB maximum message size for GRPC would be well served with the more simple approach.

In our demo, we are using a 35MB image originated by the James Webb Space Telescope, 
[MACS J0417.5-1154 Wide Field](https://webbtelescope.org/contents/media/images/2024/128/01J6CXCDNSGF87TZEX379WHDXB)
, 4623 X 4623, PNG (35.14 MB).

{%
include figure image_path="/assets/images/2024/10/01_select_upload_image.jpg"
caption="Step 1: Initial UI screen"
img_style="height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/02_image_picker.jpg"
caption="Step 2: Select upload file"
img_style="height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/03_upload_progress.jpg"
caption="Step 3: File upload to server"
img_style="height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/04_upload_complete.jpg"
caption="Step 4: File available on server"
img_style="height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/05_download_progress.jpg"
caption="Step 5: Download from server to client"
img_style="height: 450px;"
%}

{%
include figure image_path="/assets/images/2024/10/06_download_complete.jpg"
caption="Step 6: Successful upload and download"
img_style="height: 450px;"
%}

# Flutter Code

This implementation will use the state management built into Flutter: 
- [ChangeNotifier](https://api.flutter.dev/flutter/foundation/ChangeNotifier-class.html) and [ListenableBuilder](https://api.flutter.dev/flutter/widgets/ListenableBuilder-class.html)
- [ValueNotifier](https://api.flutter.dev/flutter/foundation/ValueNotifier-class.html) and [ValueListenableBuilder](https://api.flutter.dev/flutter/widgets/ValueListenableBuilder-class.html)

These work in the same way, with `ValueNotifier` being an implementation inheriting from `ChangeNotifier` suitable for
simplified immutable state. The `ChangeNotifier` and `ValueNotifier` are data classes storing an internal state, which 
are rendered by `ListenableBuilder` and `ValueListenableBuilder` widgets, with the ability to trigger widget rendering
on state changes.

There are 5 states to watch in this workflow:
- `ValueNotifier<XFile?>` representing a selected upload image (`XFile`) on the client, chosen by _ImagePicker_.
- `ValueNotifier<FileSendChangeNotifier?>` represented by either `null` when there isn't an upload, or non-null for an upload.
- `FileSendChangeNotifier` represents an upload process, either actively uploading or completed.
- `ValueNotifier<FileReceiveChangeNotifier?>` represented by either `null` when there isn't a download, or non-null for a download.
- `FileReceiveChangeNotifier` represents a download process, either actively downloading or completed.

## Code: file_transfer_change_notifier.dart

Both uploads and downloads have been modeled using an abstract class `FileTransferChangeNotifier`.
This class will track the transfer process, with `update` being called as each `FileChunk` of the stream is 
processed.

A `close` method should be called after a successful transfer has completed.

The implementations for upload and download require different implementations because of how stream processing has
been implemented: uploads process stream elements before they are uploaded (ie: transfer will happen), and downloads
process stream elements after they are downloaded (ie: transfer has happened).

```dart
abstract class FileTransferChangeNotifier with ChangeNotifier {
  /// State
  FileTransferProgress _progress;
  DateTime _lastUpdate;

  /// Used to render UI Widgets
  FileTransferProgress get progress => _progress;
  
  /// `fileChunk` is about to be sent on send, or has been received on receive.
  void update(FileChunk fileChunk);

  /// Mark the transfer as successful and complete.
  /// `filename` argument should be the server reference on send, 
  ///  or the local file path if received.
  void close(String filename);
}
```
### FileSendChangeNotifier

This class implements the upload process, each `update` call will mark the current `FileChunk` as in progress and all
previous as completed, with `close` marking the current `FileChunk` as completed.

### FileReceiveChangeNotifier

This class implements the download process, each `update` call will mark the current `FileChunk` as completed, with 
`close` unnecessary but for performing the client-server filename mapping should it exist.

## Code: file_transfer_progress.dart

This class is the immutable data model used by _FileSendChangeNotifier_ and _FileReceiveChangeNotifier_.

```dart
class FileTransferProgress {

  //Populated when process starts.
  // null only for an upload that hasn't processed its first FileChunk
  final DateTime? startTimestamp;
  
  //Populated by calling `close`
  final DateTime? endTimestamp;
  
  //Populated from first FileChunk
  final int fileSizeInBytes;
  final int chunkSizeInBytes;
  
  //Calculated when each FileChunk is processed
  final int transferredBytes;
  final int bytesPerSecond;
  final int secondsRemaining;
  
  //Populated when process starts
  //Updated by calling `close`
  final String? filename;
}
```

## Code: file_transfer_grpc_client.dart

This class implements the GRPC client; initiating GRPC connections and requests, as well as handling `Stream` creation 
to and from the local filesystem.

```dart
class FileTransferGrpcClient {
  /// Code-generated client
  final FileServiceClient _fileServiceClient;
  
  /// Requests
  Future<FileSendChangeNotifier> upload(XFile xFile)
  FileReceiveChangeNotifier download(String serverFilename, File output)
  
  /// Static Helpers
  static Stream<(Uint8List, int)> _calcOffset<T>(Stream<Uint8List> input)
  static Stream<Uint8List> _rechunkStream(Stream<Uint8List> input)
  static Iterable<Uint8List> _rechunkUint8List(Uint8List list, int length)
  static Future<Stream<FileChunk>> _readXFile(XFile xFile)
}
```




## Flutter Widget: file_transfer_progress_bar_widget.dart


## Flutter Widget: file_transfer_widget.dart

```dart
class FileTransferProgressBarWidget extends StatelessWidget {
  final ValueNotifier<FileTransferChangeNotifier?> fileTransferChangeNotifierNotifier;

  /// Returns % progress, display text: line1, line 2
  /// action = 'upload' or 'download'
  static (double, String, String) calculateTextAndProgress(
    FileTransferProgress fileTransferProgress, 
    String action,
  )
  
  @override
  Widget build(BuildContext context) {
    /// Displays a Column([ProgressBar, Text(line1), Text(line2)]);
    return ValueListenableBuilder();
  }
}
```