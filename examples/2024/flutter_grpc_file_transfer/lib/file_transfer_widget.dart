import 'dart:io';
import 'dart:math';
import 'dart:typed_data';
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
  final _downloadImage = ValueNotifier<Uint8List?>(null);
  final _downloadProgress = ValueNotifier<FileTransferChangeNotifier?>(null);

  void selectUploadImage() async {
    final xFile = await ImagePicker().pickImage(source: ImageSource.gallery);
    _uploadImage.value = xFile;
    if (xFile != null) {
      _uploadProgress.value = await fileTransferGrpcClient.upload(xFile);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        ListenableBuilder(
          listenable: _uploadImage,
          builder: (context, child) {
            final xFile = _uploadImage.value;
            if (xFile == null) {
              return ElevatedButton(
                onPressed: selectUploadImage,
                child: const SizedBox(
                  width: 320,
                  height: 240,
                  child: Align(
                    alignment: Alignment.center,
                    child: Text("Select Upload Image", textAlign: TextAlign.center),
                  ),
                ),
              );
            } else {
              final filename = xFile.path.substring(max(0, xFile.path.lastIndexOf("/")));
              return Column(
                children: [
                  ElevatedButton(
                    onPressed: selectUploadImage,
                    child: Container(
                      width: 320,
                      height: 240,
                      decoration: BoxDecoration(
                        image: DecorationImage(
                          image: FileImage(File(xFile.path)),
                          fit: BoxFit.scaleDown,
                        ),
                      ),
                    ),
                  ),
                  FileTransferProgressBarWidget(fileTransferChangeNotifierNotifier: _uploadProgress),
                ],
              );
            }
          },
        ),
        ListenableBuilder(
          listenable: _uploadProgress,
          builder: (context, child) {
            final filename = _uploadProgress.value?.progress.filename;
            if (filename == null) {
              return Container();
            } else {
              return ListenableBuilder(
                listenable: _downloadProgress,
                builder: (context, child) {
                  if (_downloadProgress.value == null) {
                    return ElevatedButton(
                      onPressed: selectUploadImage,
                      child: const SizedBox(
                        width: 320,
                        height: 240,
                        child: Align(
                          alignment: Alignment.center,
                          child: Text("Download Upload Image", textAlign: TextAlign.center),
                        ),
                      ),
                    );
                  } else {
                    return ListenableBuilder(
                      listenable: _downloadImage,
                      builder: (context, child) {
                        final bytes = _downloadImage.value;
                        if (bytes == null) {
                          return FileTransferProgressBarWidget(fileTransferChangeNotifierNotifier: _downloadProgress);
                        } else {
                          return Column(
                            children: [
                              Container(
                                width: 320,
                                height: 240,
                                decoration: BoxDecoration(
                                  image: DecorationImage(
                                    image: MemoryImage(bytes),
                                    fit: BoxFit.cover,
                                  ),
                                ),
                              ),
                              FileTransferProgressBarWidget(fileTransferChangeNotifierNotifier: _downloadProgress),
                            ],
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
