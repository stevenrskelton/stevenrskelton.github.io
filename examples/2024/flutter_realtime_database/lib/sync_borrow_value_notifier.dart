
import 'package:flutter/widgets.dart';
import 'package:flutter_realtime_database/generated/protobuf/sync_service.pb.dart';
import 'package:flutter_realtime_database/sync_change_notifier.dart';

class SyncBorrowValueNotifier extends ValueNotifier<Set<Data>> {
  SyncBorrowValueNotifier(this.syncChangeNotifier): super({});

  final SyncChangeNotifier syncChangeNotifier;
  final Set<int> borrowed = {};

  void borrow(int id) => syncChangeNotifier.borrow(this, id);

  void release(int id) => syncChangeNotifier.release(this, id);

  @override
  void dispose(){
    syncChangeNotifier.releaseBorrower(this);
    super.dispose();
  }
}