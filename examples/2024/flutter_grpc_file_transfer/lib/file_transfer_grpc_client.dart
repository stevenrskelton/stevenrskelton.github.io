import 'dart:math';

import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_change_notifier.dart';
import 'package:flutter_grpc_file_transfer/generated/protobuf/file_service.pbgrpc.dart';
import 'package:grpc/grpc.dart';
import 'package:image_picker/image_picker.dart';

import 'dart:io';
import 'dart:typed_data';
import 'package:fixnum/fixnum.dart';

class FileTransferGrpcClient {
  static const uploadFileChunkSize = 4000;

  factory FileTransferGrpcClient.localhost() {
    final channel = ClientChannel(
      '127.0.0.1',
      port: 50051,
      options: const ChannelOptions(credentials: ChannelCredentials.insecure()),
    );
    return FileTransferGrpcClient(FileServiceClient(channel));
  }

  FileTransferGrpcClient(this._fileServiceClient);

  final FileServiceClient _fileServiceClient;

  Future<FileTransferChangeNotifier> upload(XFile xFile) async {
    final fileSizeInBytes = await xFile.length();
    final fileTransferChangeNotifier = FileTransferChangeNotifier(fileSizeInBytes);
    final fileChunkStream = await _readXFile(xFile);
    fileChunkStream.map((fileChunk) {
      fileTransferChangeNotifier.update(fileChunk);
      return fileChunk;
    });
    _fileServiceClient.setFile(fileChunkStream).then((setFileResponse) {
      fileTransferChangeNotifier.close(setFileResponse.filename);
    });
    return fileTransferChangeNotifier;
  }

  FileTransferChangeNotifier download(String serverFilename, File output) {
    final getFileRequest = GetFileRequest(
      filename: serverFilename,
    );
    final fileTransferChangeNotifier = FileTransferChangeNotifier(0);
    _fileServiceClient.getFile(getFileRequest).fold(false, (s, fileChunk) {
      fileTransferChangeNotifier.update(fileChunk);
      output.writeAsBytesSync(fileChunk.body, mode: FileMode.append);
      return fileChunk.success;
    }).then((isSuccess) {
      if (isSuccess) {
        fileTransferChangeNotifier.close(output.path);
      }
    });
    return fileTransferChangeNotifier;
  }

  static Stream<(Uint8List, int)> _calcOffset<T>(Stream<Uint8List> input) async* {
    var offset = 0;
    await for (final chunk in input) {
      yield (chunk, offset);
      offset = offset + chunk.length;
    }
  }

  static Stream<Uint8List> _rechunkStream(Stream<Uint8List> input) {
    return input.expand((chunk) {
      if (kDebugMode) {
        print("IO read chunk size ${chunk.length}");
      }
      if (chunk.lengthInBytes == uploadFileChunkSize) {
        return [chunk];
      } else {
        return _rechunkUint8List(chunk, uploadFileChunkSize);
      }
    });
  }

  static Iterable<Uint8List> _rechunkUint8List(Uint8List list, int length) sync* {
    for (var i = 0; i < list.length; i += uploadFileChunkSize) {
      yield Uint8List.sublistView(list, i, min(i + length, list.length));
    }
  }

  static Future<Stream<FileChunk>> _readXFile(XFile xFile) async {
    final size = await xFile.length();
    final bodyStream = xFile.openRead();
    final chunkedStream = _rechunkStream(bodyStream);
    final offsetStream = _calcOffset(chunkedStream);
    return offsetStream.map((listUInt8List) {
      final (body, offset) = listUInt8List;
      return FileChunk(
        filename: xFile.name,
        size: Int64(size),
        offset: Int64(offset),
        success: (body.length + offset) == size,
        body: body,
      );
    });
  }
}
