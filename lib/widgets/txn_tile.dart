import 'package:flutter/material.dart';

import '../core/money.dart';
import '../core/text_utils.dart';
import '../data/models.dart';

String methodLabel(PayMethod m) => switch (m) {
      PayMethod.cash => 'Cash',
      PayMethod.upi => 'UPI',
      PayMethod.other => 'Other',
    };

class TxnTile extends StatelessWidget {
  const TxnTile({
    super.key,
    required this.txn,
    this.customerName,
    this.onReverse,
  });

  final LedgerTxn txn;
  final String? customerName;

  /// Shown only when the entry can still be reversed.
  final VoidCallback? onReverse;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isUdhaari = txn.type == TxnType.udhaari;
    final color = isUdhaari ? scheme.error : scheme.primary;
    final label = txn.isReversal
        ? 'Reversal (${isUdhaari ? 'Udhaari' : 'Jama'})'
        : (isUdhaari ? 'Udhaari' : 'Jama');
    final subtitle = [
      if (customerName != null) customerName!,
      '${formatDateTime(txn.createdAt)} · ${methodLabel(txn.method)}',
    ].join('\n');

    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        isUdhaari ? Icons.arrow_upward : Icons.arrow_downward,
        color: color,
      ),
      title: Text(label),
      subtitle: Text(subtitle),
      isThreeLine: customerName != null,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            formatPaise(txn.amountPaise),
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w700,
              fontSize: 16,
            ),
          ),
          if (onReverse != null)
            IconButton(
              tooltip: 'Galti sudharein (Reverse)',
              icon: const Icon(Icons.undo),
              onPressed: onReverse,
            ),
        ],
      ),
    );
  }
}
