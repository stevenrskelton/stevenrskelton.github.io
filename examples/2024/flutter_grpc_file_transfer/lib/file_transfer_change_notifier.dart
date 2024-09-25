import 'dart:math';
import 'package:flutter/widgets.dart';
import 'package:fixnum/fixnum.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_progress.dart';
import 'generated/protobuf/file_service.pb.dart';

class FileTransferChangeNotifier with ChangeNotifier {
  static const _smoothingFactor = 0.75;

  static double exponentialMovingAverage(double x, double previous) {
    return x * _smoothingFactor + (1 - _smoothingFactor) * previous;
  }

  FileTransferChangeNotifier(FileChunk first)
      : _progress = FileTransferProgress(
          startTime: DateTime.timestamp(),
          fileSize: first.size,
          transferred: Int64.ZERO,
          bytesPerSecond: 0,
          secondsRemaining: 0,
        ),
        _lastUpdate = DateTime.timestamp();

  FileTransferProgress _progress;
  DateTime _lastUpdate;

  FileTransferProgress get progress => _progress;

  void update(FileChunk fileChunk) {
    final now = DateTime.timestamp();

    final lastChunkSizeInBytes = fileChunk.offset - _progress.transferred;
    final lastChunkDurationInSeconds = max(1, now.difference(_lastUpdate).inMilliseconds) / 1000;
    final lastChunkBytesPerSecond = lastChunkSizeInBytes.toDouble() / lastChunkDurationInSeconds;

    final bytesPerSecond = _progress.transferred.isZero
        ? lastChunkBytesPerSecond
        : exponentialMovingAverage(lastChunkBytesPerSecond, _progress.bytesPerSecond);
    final bytesRemaining = _progress.fileSize - fileChunk.offset;
    final secondsRemaining = bytesRemaining ~/ bytesPerSecond;

    _progress = FileTransferProgress(
      startTime: _progress.startTime,
      fileSize: _progress.fileSize,
      transferred: _progress.transferred + fileChunk.offset,
      bytesPerSecond: bytesPerSecond,
      secondsRemaining: secondsRemaining.toInt(),
    );
    _lastUpdate = now;
    notifyListeners();
  }
}
