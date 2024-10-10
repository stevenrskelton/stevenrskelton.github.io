import 'package:flutter/material.dart';
import 'package:flutter_realtime_database/generated/protobuf/sync_service.pbgrpc.dart';
import 'package:flutter_realtime_database/sync_change_notifier.dart';
import 'package:flutter_realtime_database/sync_client_widget.dart';
import 'package:grpc/grpc.dart';

void main() {

  final channel = ClientChannel(
    '10.0.2.2', //localhost for Android emulators
    //'localhost',
    port: 50051,
    options: const ChannelOptions(credentials: ChannelCredentials.insecure()),
  );
  final syncServiceClient = SyncServiceClient(channel);
  final syncChangeNotifier = SyncChangeNotifier(syncServiceClient);

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
      body: Row(
        children: [
          SyncClientWidget(syncChangeNotifier),
          SyncClientWidget(syncChangeNotifier),
          SyncClientWidget(syncChangeNotifier),
        ],
      ),
    ),
  ));
}