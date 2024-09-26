import 'dart:math';
import 'package:flutter/widgets.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_progress.dart';
import 'generated/protobuf/file_service.pb.dart';

abstract class FileTransferChangeNotifier with ChangeNotifier {
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

  void update(FileChunk fileChunk);

  void close(String filename);
}

class FileSendChangeNotifier extends FileTransferChangeNotifier {
  FileSendChangeNotifier(super.fileSizeInBytes);

  @override
  void update(FileChunk fileChunk) {
    final now = DateTime.timestamp();
    if (_progress.startTimestamp == null) {
      _progress = FileTransferProgress(
        startTimestamp: now,
        fileSizeInBytes: _progress.fileSizeInBytes,
        chunkSizeInBytes: fileChunk.body.length,
      );
    } else {
      final lastChunkSizeInBytes = fileChunk.offset - _progress.transferredBytes;
      final lastChunkDurationInSeconds = max(1, now.difference(_lastUpdate).inMilliseconds) / 1000;
      final lastChunkBytesPerSecond = lastChunkSizeInBytes.toDouble() / lastChunkDurationInSeconds;

      final bytesPerSecond = _progress.transferredBytes == 0
          ? lastChunkBytesPerSecond
          : FileTransferChangeNotifier.exponentialMovingAverage(lastChunkBytesPerSecond, _progress.bytesPerSecond.toDouble());
      final transferredBytes = fileChunk.offset.toInt();
      final bytesRemaining = _progress.fileSizeInBytes - transferredBytes;

      _progress = FileTransferProgress(
        startTimestamp: _progress.startTimestamp,
        fileSizeInBytes: _progress.fileSizeInBytes,
        transferredBytes: transferredBytes,
        bytesPerSecond: bytesPerSecond.toInt(),
        secondsRemaining: (bytesRemaining / bytesPerSecond).ceil(),
        chunkSizeInBytes: fileChunk.body.length,
      );
    }
    _lastUpdate = now;
    notifyListeners();
  }

  @override
  void close(String filename) {
    final startTimestamp = _progress.startTimestamp;
    if (startTimestamp == null) {
      throw Exception("Transfer not started.");
    }

    final transferredBytes = _progress.transferredBytes + _progress.chunkSizeInBytes;
    if (transferredBytes != _progress.fileSizeInBytes) {
      throw Exception("Expected ${_progress.fileSizeInBytes}, only $transferredBytes transferred.");
    }

    final now = DateTime.timestamp();
    final durationInSeconds = max(1, now.difference(startTimestamp).inMilliseconds) / 1000;
    _progress = FileTransferProgress(
      startTimestamp: _progress.startTimestamp,
      endTimestamp: now,
      fileSizeInBytes: _progress.fileSizeInBytes,
      transferredBytes: transferredBytes,
      bytesPerSecond: transferredBytes ~/ durationInSeconds,
      filename: filename,
    );
    _lastUpdate = now;
    notifyListeners();
  }
}

class FileReceiveChangeNotifier extends FileTransferChangeNotifier {
  FileReceiveChangeNotifier() : super(0);

  @override
  void update(FileChunk fileChunk) {
    final now = DateTime.timestamp();
    if (fileChunk.success) {
      final startTimestamp = _progress.startTimestamp ?? _lastUpdate;
      final durationInSeconds = max(1, now.difference(startTimestamp).inMilliseconds) / 1000;
      final transferredBytes = fileChunk.fileSize.toInt();
      _progress = FileTransferProgress(
        startTimestamp: startTimestamp,
        endTimestamp: now,
        fileSizeInBytes: transferredBytes,
        transferredBytes: transferredBytes,
        bytesPerSecond: transferredBytes ~/ durationInSeconds,
      );
    } else {
      final lastChunkSizeInBytes = fileChunk.body.length;
      final lastChunkDurationInSeconds = max(1, now.difference(_lastUpdate).inMilliseconds) / 1000;
      final lastChunkBytesPerSecond = lastChunkSizeInBytes.toDouble() / lastChunkDurationInSeconds;

      final transferredBytes = fileChunk.offset.toInt() + fileChunk.body.length;

      final bytesPerSecond = _progress.bytesPerSecond == 0
          ? lastChunkBytesPerSecond
          : FileTransferChangeNotifier.exponentialMovingAverage(lastChunkBytesPerSecond, _progress.bytesPerSecond.toDouble());
      final bytesRemaining = _progress.fileSizeInBytes - transferredBytes;
      final secondsRemaining = (bytesRemaining / bytesPerSecond).ceil();

      _progress = FileTransferProgress(
        startTimestamp: _progress.startTimestamp ?? _lastUpdate,
        fileSizeInBytes: fileChunk.fileSize.toInt(),
        transferredBytes: transferredBytes,
        bytesPerSecond: bytesPerSecond.toInt(),
        secondsRemaining: secondsRemaining,
        chunkSizeInBytes: fileChunk.body.length,
      );
    }

    _lastUpdate = now;
    notifyListeners();
  }

  @override
  void close(String filename) {
    _progress = FileTransferProgress(
      startTimestamp: _progress.startTimestamp,
      endTimestamp: _progress.endTimestamp,
      fileSizeInBytes: _progress.fileSizeInBytes,
      transferredBytes: _progress.transferredBytes,
      bytesPerSecond: _progress.bytesPerSecond,
      filename: filename,
    );
    _lastUpdate = DateTime.timestamp();
    notifyListeners();
  }
}
