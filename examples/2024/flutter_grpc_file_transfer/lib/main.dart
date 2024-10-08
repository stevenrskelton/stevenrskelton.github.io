import 'package:flutter/material.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_grpc_client.dart';
import 'package:flutter_grpc_file_transfer/file_transfer_widget.dart';

void main() {
  runApp(MaterialApp(
    theme: ThemeData(
      useMaterial3: true,
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ButtonStyle(
          shape: WidgetStateProperty.all<OutlinedBorder>(RoundedRectangleBorder(borderRadius: BorderRadius.circular(4))),
        ),
      ),
      cardTheme: CardTheme(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
        // margin: EdgeInsets.zero,
        // color: Colors.blueGrey,
      ),
    ),
    home: Scaffold(
      body: Container(
        color: Colors.grey,
        child: FileTransferWidget(fileTransferGrpcClient: FileTransferGrpcClient.localhost()),
      ),
    ),
  ));
}
