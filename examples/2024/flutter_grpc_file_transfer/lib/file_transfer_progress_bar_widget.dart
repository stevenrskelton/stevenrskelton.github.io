import 'package:flutter/material.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_change_notifier.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_progress.dart';

class FileTransferProgressBarWidget extends StatelessWidget {
  const FileTransferProgressBarWidget({super.key, required this.fileTransferChangeNotifierNotifier});

  final ValueNotifier<FileTransferChangeNotifier?> fileTransferChangeNotifierNotifier;

  static (double, String, String) calculateTextAndProgress(FileTransferProgress fileTransferProgress, String action) {
    if (fileTransferProgress.startTimestamp == null || fileTransferProgress.fileSizeInBytes == 0) {
      if (fileTransferProgress.fileSizeInBytes == 0) {
        return (0, "Awaiting $action start", "");
      } else {
        return (0, "Awaiting $action start", "${fileTransferProgress.fileSizeInBytes} bytes");
      }
    } else {
      if (fileTransferProgress.endTimestamp == null) {
        final progress = fileTransferProgress.transferredBytes.toDouble() / fileTransferProgress.fileSizeInBytes.toDouble();
        final bytesRemaining = "$action ${fileTransferProgress.transferredBytes} of ${fileTransferProgress.fileSizeInBytes}";
        final timeRemaining = "${fileTransferProgress.bytesPerSecond} bytes/sec, ${fileTransferProgress.secondsRemaining} seconds remaining.";
        return (progress, bytesRemaining, timeRemaining);
      } else {
        final bytesCompleted = "Completed $action of ${fileTransferProgress.fileSizeInBytes} bytes";
        final secondsDuration = (fileTransferProgress.endTimestamp!.difference(fileTransferProgress.startTimestamp!).inMilliseconds / 1000).toStringAsFixed(2);
        final timeElapsed = "${fileTransferProgress.bytesPerSecond} bytes/sec for $secondsDuration seconds.";
        return (1, bytesCompleted, timeElapsed);
      }
    }
  }

  static String _action(FileTransferChangeNotifier obj) {
    if (obj is FileSendChangeNotifier) {
      return "upload";
    } else if (obj is FileReceiveChangeNotifier) {
      return "download";
    } else {
      return "";
    }
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder(
      valueListenable: fileTransferChangeNotifierNotifier,
      builder: (context, fileTransferChangeNotifier, child) {
        if (fileTransferChangeNotifier == null) {
          return Container();
        } else {
          return ListenableBuilder(
            listenable: fileTransferChangeNotifier,
            builder: (context, child) {
              final (value, line1, line2) = calculateTextAndProgress(fileTransferChangeNotifier.progress, _action(fileTransferChangeNotifier));
              return Column(
                children: [
                  LinearProgressIndicator(value: value, minHeight: 12),
                  Text(line1),
                  Text(line2),
                ],
              );
            },
          );
        }
      },
    );
  }
}
