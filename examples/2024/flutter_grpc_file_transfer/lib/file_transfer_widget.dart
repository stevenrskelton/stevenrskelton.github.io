import 'dart:io';
import 'dart:math';
import 'dart:typed_data';
import 'package:fixnum/fixnum.dart';
import 'package:flutter/material.dart';

import 'package:flutter_grpc_file_transfer/generated/protobuf/file_service.pb.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';

class FileTransferWidget extends StatelessWidget {
  static const LOCAL_FILE_CHUNK_SIZE = 65536;

  final uploadImage = ValueNotifier<XFile?>(null);
  final downloadImage = ValueNotifier<Uint8List?>(null);



  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ListenableBuilder(
          listenable: uploadImage,
          builder: (context, child) {
            final xFile = uploadImage.value;
            if (xFile == null) {
              return Container();
            } else {
              final filename = xFile.path.substring(max(0, xFile.path.lastIndexOf("/")));
              return Column(
                children: [
                  OutlinedButton(
                    onPressed: () {
                      ImagePicker().pickImage(source: ImageSource.gallery).then((xFile) => uploadImage.value = xFile);
                    },
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
                  FilledButton(
                    onPressed: onPressed,
                    child: Text(filename),
                  ),
                ],
              );
            }
          },
        ),
        SizedBox(
          width: 400,
          height: 400,
          child: ListenableBuilder(
              listenable: downloadImage,
              builder: (context, child) {
                final bytes = downloadImage.value;
                if (bytes == null) {
                  return Container();
                } else {
                  return Container(
                    decoration: BoxDecoration(
                      image: DecorationImage(
                        image: MemoryImage(bytes),
                        fit: BoxFit.cover,
                      ),
                    ),
                  );
                }
              }),
        ),
      ],
    );
  }
}
