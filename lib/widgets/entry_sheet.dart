import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../core/money.dart';
import '../data/models.dart';
import '../services.dart';
import 'txn_tile.dart';

/// Bottom sheet to record one Udhaari or Jama entry.
/// Payment method is only a label; no money moves.
Future<void> showEntrySheet(
  BuildContext context, {
  required String customerId,
  required String customerName,
  required TxnType type,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (sheetContext) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(sheetContext).viewInsets.bottom),
      child: _EntrySheet(
        customerId: customerId,
        customerName: customerName,
        type: type,
      ),
    ),
  );
}

class _EntrySheet extends StatefulWidget {
  const _EntrySheet({
    required this.customerId,
    required this.customerName,
    required this.type,
  });

  final String customerId;
  final String customerName;
  final TxnType type;

  @override
  State<_EntrySheet> createState() => _EntrySheetState();
}

class _EntrySheetState extends State<_EntrySheet> {
  static const List<int> _quickRupees = [50, 100, 200, 500, 1000];

  final TextEditingController _amount = TextEditingController();
  // One key per opened sheet: a double tap or retry cannot create two entries.
  final String _key = const Uuid().v4();
  PayMethod _method = PayMethod.cash;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving) return;
    final paise = parseRupeesToPaise(_amount.text);
    if (paise == null || paise < 1) {
      setState(() => _error = 'Sahi amount likhein (jaise 50 ya 50.50)');
      return;
    }
    final services = ServicesScope.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await services.ledger.addTransaction(
        customerId: widget.customerId,
        type: widget.type,
        amountPaise: paise,
        method: _method,
        idempotencyKey: _key,
      );
      navigator.pop();
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            '${widget.type == TxnType.udhaari ? 'Udhaari' : 'Jama'} '
            '${formatPaise(paise)} save ho gaya',
          ),
        ),
      );
    } on LedgerException catch (e) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = 'Save nahi ho paya. Dobara koshish karein.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isUdhaari = widget.type == TxnType.udhaari;
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '${isUdhaari ? '+ Udhaari' : '- Jama'}: ${widget.customerName}',
            style: theme.textTheme.titleLarge,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _amount,
            autofocus: true,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            style: theme.textTheme.headlineSmall,
            decoration: InputDecoration(
              prefixText: '₹ ',
              labelText: 'Amount',
              errorText: _error,
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final r in _quickRupees)
                ActionChip(
                  label: Text('₹$r'),
                  onPressed: () => setState(() {
                    _amount.text = r.toString();
                    _error = null;
                  }),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Text('Kaise (sirf record ke liye)', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final m in PayMethod.values)
                ChoiceChip(
                  label: Text(methodLabel(m)),
                  selected: _method == m,
                  onSelected: (_) => setState(() => _method = m),
                ),
            ],
          ),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Save'),
          ),
        ],
      ),
    );
  }
}
