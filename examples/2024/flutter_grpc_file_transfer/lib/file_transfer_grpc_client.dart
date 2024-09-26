import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_change_notifier.dart';
import 'package:flutter_grpc_file_transfer/generated/protobuf/file_service.pbgrpc.dart';
import 'package:grpc/grpc.dart';
import 'package:image_picker/image_picker.dart';

import 'dart:io';
import 'package:fixnum/fixnum.dart';

class FileTransferGrpcClient {
  static const uploadFileChunkSize = 4000;

  factory FileTransferGrpcClient.localhost() {
    final channel = ClientChannel(
      '10.0.2.2',
      port: 50051,
      options: const ChannelOptions(credentials: ChannelCredentials.insecure()),
    );
    return FileTransferGrpcClient(FileServiceClient(channel));
  }

  FileTransferGrpcClient(this._fileServiceClient);

  final FileServiceClient _fileServiceClient;

  Future<FileSendChangeNotifier> upload(XFile xFile) async {
    final fileSizeInBytes = await xFile.length();
    final fileSendChangeNotifier = FileSendChangeNotifier(fileSizeInBytes);
    final fileChunkStream = await _readXFile(xFile);
    final tapStream = fileChunkStream.map((fileChunk) {
      fileSendChangeNotifier.update(fileChunk);
      return fileChunk;
    });
    _fileServiceClient.setFile(tapStream).then((setFileResponse) {
      fileSendChangeNotifier.close(setFileResponse.filename);
    });
    return fileSendChangeNotifier;
  }

  FileReceiveChangeNotifier download(String serverFilename, File output) {
    final getFileRequest = GetFileRequest(
      filename: serverFilename,
    );
    final fileReceiveChangeNotifier = FileReceiveChangeNotifier();
    _fileServiceClient.getFile(getFileRequest).fold(false, (s, fileChunk) {
      if (kDebugMode) {
        print("Received chunk size ${fileChunk.body.length}");
      }
      fileReceiveChangeNotifier.update(fileChunk);
      output.writeAsBytesSync(fileChunk.body, mode: FileMode.append);
      return fileChunk.success;
    }).then((isSuccess) {
      if (isSuccess) {
        fileReceiveChangeNotifier.close(output.path);
      }
    });
    return fileReceiveChangeNotifier;
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
      final splitList = Uint8List.sublistView(list, i, min(i + length, list.length));
      if (kDebugMode) {
        print("IO split to chunk length ${splitList.length}");
      }
      yield splitList;
    }
  }

  static Future<Stream<FileChunk>> _readXFile(XFile xFile) async {
    final fileSize = await xFile.length();
    final bodyStream = xFile.openRead();
    final chunkedStream = _rechunkStream(bodyStream);
    final offsetStream = _calcOffset(chunkedStream);
    return offsetStream.map((listUInt8List) {
      final (body, offset) = listUInt8List;
      return FileChunk(
        filename: xFile.name,
        fileSize: Int64(fileSize),
        offset: Int64(offset),
        success: (body.length + offset) == fileSize,
        body: body,
      );
    });
  }
}
