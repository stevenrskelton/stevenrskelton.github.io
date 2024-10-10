import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:flutter_realtime_database/sync_change_notifier.dart';

class SyncClientWidget extends StatefulWidget {
  const SyncClientWidget(this.syncChangeNotifier, {super.key});

  final SyncChangeNotifier syncChangeNotifier;

  @override
  State<SyncClientWidget> createState() => _SyncClientWidgetState();
}

class _SyncClientWidgetState extends State<SyncClientWidget> {
  late final syncBorrowValueNotifier = widget.syncChangeNotifier.createBorrower();

  @override
  Widget build(BuildContext context) {
    return Card(child : ValueListenableBuilder(
      valueListenable: syncBorrowValueNotifier,
      builder: (context, dataSet, child) {
        return Row(
          children: [1, 2, 3].map((id) {
            final data = dataSet.firstWhereOrNull((d) => d.id == id);
            return Row(
              children: [
                Text(data?.field1 ?? ''),
                FilledButton(
                  onPressed: () {
                    if (data == null) {
                      syncBorrowValueNotifier.borrowed.add(id);
                    } else {
                      syncBorrowValueNotifier.borrowed.remove(id);
                    }
                  },
                  child: Text(id.toString()),
                ),
              ],
            );
          }).toList(),
        );
      },
    ));
  }

  @override
  void dispose() {
    syncBorrowValueNotifier.dispose();
    super.dispose();
  }
}
