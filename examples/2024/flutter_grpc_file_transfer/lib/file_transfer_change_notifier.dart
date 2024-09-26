import 'dart:math';
import 'package:flutter/widgets.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_progress.dart';
import 'generated/protobuf/file_service.pb.dart';

class FileTransferChangeNotifier with ChangeNotifier {
  static const _smoothingFactor = 0.75;

  static double exponentialMovingAverage(double x, double previous) {
    return x * _smoothingFactor + (1 - _smoothingFactor) * previous;
  }

  FileTransferChangeNotifier(int fileSizeInBytes)
      : _progress = FileTransferProgress(fileSizeInBytes: fileSizeInBytes),
        _lastUpdate = DateTime.timestamp();

  FileTransferProgress _progress;
  DateTime _lastUpdate;

  FileTransferProgress get progress => _progress;

  void close(String filename) {
    final startTimestamp = _progress.startTimestamp;
    if(startTimestamp == null) {
      throw Exception("Transfer not started.");
    }

    final transferredBytes = _progress.transferredBytes + _progress.chunkSizeInBytes;
    if(transferredBytes != _progress.fileSizeInBytes){
      throw Exception("Expected ${_progress.fileSizeInBytes}, only $transferredBytes transferred.");
    }

    final now = DateTime.timestamp();
    final durationInSeconds = max(1, now.difference(startTimestamp).inMilliseconds) / 1000;
    _progress = FileTransferProgress(
      startTimestamp: _progress.startTimestamp,
      endTimestamp: now,
      fileSizeInBytes: _progress.fileSizeInBytes,
      transferredBytes: transferredBytes,
      bytesPerSecond: transferredBytes / durationInSeconds,
      secondsRemaining: -1,
      filename: filename,
    );
  }

  void update(FileChunk fileChunk) {
    final now = DateTime.timestamp();
    if(_progress.startTimestamp == null) {
      _progress = FileTransferProgress(
        startTimestamp: now,
        fileSizeInBytes: _progress.fileSizeInBytes,
      );
    } else {
      final lastChunkSizeInBytes = fileChunk.offset - _progress.transferredBytes;
      final lastChunkDurationInSeconds = max(1, now.difference(_lastUpdate).inMilliseconds) / 1000;
      final lastChunkBytesPerSecond = lastChunkSizeInBytes.toDouble() / lastChunkDurationInSeconds;

      final bytesPerSecond = _progress.transferredBytes == 0
          ? lastChunkBytesPerSecond
          : exponentialMovingAverage(lastChunkBytesPerSecond, _progress.bytesPerSecond);
      final bytesRemaining = _progress.fileSizeInBytes - fileChunk.offset.toInt();
      final secondsRemaining = bytesRemaining ~/ bytesPerSecond;

      _progress = FileTransferProgress(
        startTimestamp: _progress.startTimestamp,
        fileSizeInBytes: _progress.fileSizeInBytes,
        transferredBytes: _progress.transferredBytes + fileChunk.offset.toInt(),
        bytesPerSecond: bytesPerSecond,
        secondsRemaining: secondsRemaining.toInt(),
        chunkSizeInBytes: fileChunk.body.length,
      );
    }
    _lastUpdate = now;
    notifyListeners();
  }
}
