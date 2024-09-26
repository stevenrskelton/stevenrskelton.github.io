import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_change_notifier.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_grpc_client.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_progress_bar_widget.dart';

import 'package:image_picker/image_picker.dart';

class FileTransferWidget extends StatelessWidget {
  FileTransferWidget({super.key, required this.fileTransferGrpcClient});

  final FileTransferGrpcClient fileTransferGrpcClient;

  final _uploadImage = ValueNotifier<XFile?>(null);
  final _uploadProgress = ValueNotifier<FileTransferChangeNotifier?>(null);
  final _downloadProgress = ValueNotifier<FileTransferChangeNotifier?>(null);

  void selectUploadImage() async {
    final xFile = await ImagePicker().pickImage(source: ImageSource.gallery);
    _uploadImage.value = xFile;
    if (xFile != null) {
      _uploadProgress.value = await fileTransferGrpcClient.upload(xFile);
      _downloadProgress.value = null;
    }
  }

  void downloadServerImage(String filename) async {
    final tempDir = await Directory.systemTemp.createTemp('flutter_grpc_file_transfer');
    _downloadProgress.value = fileTransferGrpcClient.download(filename, File('${tempDir.path}/download.bin'));
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        ValueListenableBuilder(
          valueListenable: _uploadImage,
          builder: (context, xFile, child) {
            if (xFile == null) {
              return ElevatedButton(
                onPressed: selectUploadImage,
                child: const Text("Select Upload Image", textAlign: TextAlign.center),
              );
            } else {
              // final filename = xFile.path.substring(max(0, xFile.path.lastIndexOf("/")));
              return Card(
                child: Column(
                  children: [
                    ElevatedButton(
                      onPressed: selectUploadImage,
                      child: Container(
                        width: 320,
                        height: 240,
                        decoration: BoxDecoration(
                          image: DecorationImage(
                            image: FileImage(File(xFile.path)),
                            fit: BoxFit.cover,
                          ),
                        ),
                      ),
                    ),
                    FileTransferProgressBarWidget(fileTransferChangeNotifierNotifier: _uploadProgress),
                  ],
                ),
              );
            }
          },
        ),
        ValueListenableBuilder(
          valueListenable: _uploadProgress,
          builder: (context, uploadFileTransferChangeNotifier, child) {
            if (uploadFileTransferChangeNotifier == null) {
              return Container();
            } else {
              return ListenableBuilder(
                listenable: uploadFileTransferChangeNotifier,
                builder: (context, child) {
                  final uploadedServerFilename = uploadFileTransferChangeNotifier.progress.filename;
                  if (uploadedServerFilename == null) {
                    return Container();
                  } else {
                    return ValueListenableBuilder(
                      valueListenable: _downloadProgress,
                      builder: (context, downloadFileTransferChangeNotifier, child) {
                        if (downloadFileTransferChangeNotifier == null) {
                          return ElevatedButton(
                            onPressed: () => downloadServerImage(uploadedServerFilename),
                            child: const Text("Download Image", textAlign: TextAlign.center),
                          );
                        } else {
                          return ListenableBuilder(
                            listenable: downloadFileTransferChangeNotifier,
                            builder: (context, child) {
                              final localFilename = downloadFileTransferChangeNotifier.progress.filename;
                              if (localFilename == null) {
                                return FileTransferProgressBarWidget(fileTransferChangeNotifierNotifier: _downloadProgress);
                              } else {
                                return Card(
                                  child: Column(
                                    children: [
                                      ElevatedButton(
                                        onPressed: () => downloadServerImage(uploadedServerFilename),
                                        child: Container(
                                          width: 320,
                                          height: 240,
                                          decoration: BoxDecoration(
                                            image: DecorationImage(
                                              image: FileImage(File(localFilename)),
                                              fit: BoxFit.cover,
                                            ),
                                          ),
                                        ),
                                      ),
                                      FileTransferProgressBarWidget(fileTransferChangeNotifierNotifier: _downloadProgress),
                                    ],
                                  ),
                                );
                              }
                            },
                          );
                        }
                      },
                    );
                  }
                },
              );
            }
          },
        ),
      ],
    );
  }
}
