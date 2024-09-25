import 'package:fixnum/fixnum.dart';

class FileTransferProgress {
  const FileTransferProgress({
    required this.startTime,
    required this.fileSize,
    required this.transferred,
    required this.bytesPerSecond,
    required this.secondsRemaining,
  });

  final DateTime startTime;
  final Int64 fileSize;
  final Int64 transferred;
  final double bytesPerSecond;
  final int secondsRemaining;
}
