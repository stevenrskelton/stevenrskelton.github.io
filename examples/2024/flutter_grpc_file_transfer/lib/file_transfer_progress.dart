class FileTransferProgress {
  const FileTransferProgress({
    required this.fileSizeInBytes,
    this.startTimestamp,
    this.endTimestamp,
    this.transferredBytes = 0,
    this.bytesPerSecond = 0,
    this.secondsRemaining = -1,
    this.chunkSizeInBytes = 0,
    this.filename,
  });

  final DateTime? startTimestamp;
  final DateTime? endTimestamp;
  final int fileSizeInBytes;
  final int transferredBytes;
  final int bytesPerSecond;
  final int secondsRemaining;
  final int chunkSizeInBytes;
  final String? filename;
}
