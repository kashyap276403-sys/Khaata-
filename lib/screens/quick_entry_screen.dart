import 'package:flutter/material.dart';

import '../app.dart' show SettingsScope;
import '../core/money.dart';
import '../l10n/strings.dart';
import '../data/models.dart';
import '../services.dart';
import '../widgets/entry_sheet.dart';
import '../widgets/txn_tile.dart';
import 'voice_entry_screen.dart';

class _QuickData {
  const _QuickData(this.customers, this.last);
  final List<CustomerWithBalance> customers;
  final RecentTxn? last;
}

/// Quick Entry: pick Udhaari or Jama, pick a customer, type the amount.
/// Nothing is saved until the person presses Save in the entry sheet.
class QuickEntryScreen extends StatefulWidget {
  const QuickEntryScreen({
    super.key,
    this.initialType = TxnType.udhaari,
    this.showAppBar = false,
  });

  final TxnType initialType;
  final bool showAppBar;

  @override
  State<QuickEntryScreen> createState() => _QuickEntryScreenState();
}

class _QuickEntryScreenState extends State<QuickEntryScreen> {
  late TxnType _type = widget.initialType;
  String _query = '';

  Future<_QuickData> _load(AppServices s) async {
    final customers = _query.trim().isEmpty
        ? await s.customers.recentCustomers(limit: 8)
        : await s.customers.list(query: _query);
    final last = await s.ledger.lastEntry();
    return _QuickData(customers, last);
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: SettingsScope.of(context),
      builder: (context, _) => Scaffold(
      backgroundColor: Colors.transparent,
      appBar:
          widget.showAppBar
              ? AppBar(title: Text(tr(context, 'nav.quick')))
              : null,
      body: ListenableBuilder(
        listenable: services.bus,
        builder: (context, _) => FutureBuilder<_QuickData>(
          future: _load(services),
          builder: (context, snap) {
            final data = snap.data;
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (!widget.showAppBar)
                  Text(tr(context, 'nav.quick'),
                      style: theme.textTheme.headlineMedium),
                const SizedBox(height: 12),
                FilledButton.tonalIcon(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const VoiceEntryScreen(),
                    ),
                  ),
                  icon: const Icon(Icons.mic),
                  label: Text(tr(context, 'q.voice')),
                ),
                const SizedBox(height: 12),
                SegmentedButton<TxnType>(
                  style: SegmentedButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                  ),
                  segments: [
                    ButtonSegment(
                      value: TxnType.udhaari,
                      icon: Icon(Icons.add),
                      label: Text(tr(context, 'txn.udhaari')),
                    ),
                    ButtonSegment(
                      value: TxnType.jama,
                      icon: Icon(Icons.remove),
                      label: Text(tr(context, 'txn.jama')),
                    ),
                  ],
                  selected: {_type},
                  onSelectionChanged: (s) => setState(() => _type = s.first),
                ),
                const SizedBox(height: 12),
                if (data?.last != null)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.replay),
                      title: Text(
                        '${tr(context, 'q.repeat')}${data!.last!.customerName}',
                      ),
                      subtitle: Text(
                        '${data.last!.txn.type == TxnType.udhaari ? 'Udhaari' : 'Jama'} '
                        '${formatPaise(data.last!.txn.amountPaise)}'
                        '${data.last!.txn.type == TxnType.jama ? ' (${methodLabel(data.last!.txn.method)})' : ''}. '
                        '${tr(context, 'q.review')}',
                      ),
                      onTap: () => showEntrySheet(
                        context,
                        customerId: data.last!.txn.customerId,
                        customerName: data.last!.customerName,
                        type: data.last!.txn.type,
                        initialAmountPaise: data.last!.txn.amountPaise,
                        initialMethod: data.last!.txn.method,
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                TextField(
                  onChanged: (v) => setState(() => _query = v),
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.search),
                    hintText: tr(context, 'q.search'),
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  _query.trim().isEmpty
                      ? tr(context, 'q.recent')
                      : tr(context, 'q.results'),
                  style: theme.textTheme.titleMedium,
                ),
                if (snap.hasError)
                  Padding(
                    padding: const EdgeInsets.all(8),
                    child: Text('${tr(context, 'cust.loadFail')}${snap.error}'),
                  )
                else if (data == null)
                  const Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: CircularProgressIndicator()),
                  )
                else if (data.customers.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Text(
                      _query.trim().isEmpty
                          ? tr(context, 'q.emptyRecent')
                          : tr(context, 'cust.noMatch'),
                      style: theme.textTheme.bodyMedium,
                    ),
                  )
                else
                  for (final item in data.customers)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: CircleAvatar(
                        child: Text(
                            item.customer.name.characters.first.toUpperCase()),
                      ),
                      title: Text(item.customer.name),
                      subtitle: Text(item.customer.code),
                      trailing: Text(
                        formatPaise(item.balancePaise),
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      onTap: () => showEntrySheet(
                        context,
                        customerId: item.customer.id,
                        customerName: item.customer.name,
                        type: _type,
                      ),
                    ),
              ],
            );
          },
        ),
      ),
    ),
    );
  }
}
