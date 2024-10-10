import 'dart:async';
import 'package:flutter/widgets.dart';
import 'package:collection/collection.dart';
import 'package:flutter_realtime_database/generated/protobuf/sync_service.pbgrpc.dart';
import 'package:flutter_realtime_database/sync_borrow_value_notifier.dart';

class SyncChangeNotifier with ChangeNotifier {
  SyncChangeNotifier(this.syncServiceClient);

  final SyncServiceClient syncServiceClient;
  final Set<Data> state = {};
  final Set<SyncBorrowValueNotifier> borrowers = {};

  final StreamController<SyncRequest> streamController = StreamController();
  late final responseStream = syncServiceClient.bidirectionalStream(streamController.stream);
  late final subscription = responseStream.listen((syncResponse) {
    switch (syncResponse.state) {
      case SyncResponse_State.UNCHANGED:
        final existing = state.firstWhereOrNull((data) => data.id == syncResponse.data.id);
        if (existing == null) {
          state.add(syncResponse.data);
          _updateEvent(syncResponse.data.id);
        } else if (existing.etag != syncResponse.data.etag) {
          state.removeWhere((data) => data.id == syncResponse.data.id);
          state.add(syncResponse.data);
          _updateEvent(syncResponse.data.id);
        }
      case SyncResponse_State.UPDATED:
        state.removeWhere((data) => data.id == syncResponse.data.id);
        state.add(syncResponse.data);
        _updateEvent(syncResponse.data.id);
        break;
      case SyncResponse_State.LOADING:
      case SyncResponse_State.UNSUBSCRIBED:
      case SyncResponse_State.NOT_SUBSCRIBED:
      case SyncResponse_State.NOT_FOUND:
        break;
    }
  });

  void _updateEvent(int id) {
    for (final borrower in borrowers) {
      final exists = borrower.borrowed.contains(id);
      if (exists) {
        borrower.value = state.where((data) => borrower.borrowed.contains(data.id)).toSet();
      }
    }
  }

  void borrow(SyncBorrowValueNotifier borrower, int id) {
    if(borrower.borrowed.add(id)) {
      final otherReferences = borrowers.any((b) => b != borrower && b.borrowed.contains(id));
      if (!otherReferences) {
        final syncRequest = SyncRequest(subscribes: [SyncRequest_Subscribe(id: id)]);
        streamController.add(syncRequest);
      }
    }
  }

  void release(SyncBorrowValueNotifier borrower, int id) {
    if(borrower.borrowed.remove(id)) {
      final otherReferences = borrowers.any((b) => b != borrower && b.borrowed.contains(id));
      if (!otherReferences) {
        final syncRequest = SyncRequest(unsubscribes: [SyncRequest_Unsubscribe(id: id)]);
        streamController.add(syncRequest);
      }
    }
  }

  void releaseBorrower(SyncBorrowValueNotifier borrower) {
    if (borrower.borrowed.isNotEmpty) {
      final idsToRelease = borrower.borrowed.where((id) {
        return !borrowers.any((b) => b != borrower && b.borrowed.contains(id));
      });
      final syncRequest = SyncRequest(unsubscribes: idsToRelease.map((id) => SyncRequest_Unsubscribe(id: id)));
      streamController.add(syncRequest);
    }
    borrowers.remove(borrower);
  }

  SyncBorrowValueNotifier createBorrower() {
    final borrower = SyncBorrowValueNotifier(this);
    borrowers.add(borrower);
    return borrower;
  }

  @override
  void dispose() {
    subscription.cancel();
    responseStream.cancel();
    streamController.close();
    super.dispose();
  }
}
