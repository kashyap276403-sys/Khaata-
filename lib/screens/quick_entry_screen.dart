import 'package:flutter/material.dart';

import '../core/money.dart';
import '../data/models.dart';
import '../services.dart';
import '../widgets/entry_sheet.dart';
import '../widgets/txn_tile.dart';

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

    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar:
          widget.showAppBar ? AppBar(title: const Text('Quick Entry')) : null,
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
                  Text('Quick Entry', style: theme.textTheme.headlineMedium),
                const SizedBox(height: 12),
                SegmentedButton<TxnType>(
                  style: SegmentedButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                  ),
                  segments: const [
                    ButtonSegment(
                      value: TxnType.udhaari,
                      icon: Icon(Icons.add),
                      label: Text('Udhaari'),
                    ),
                    ButtonSegment(
                      value: TxnType.jama,
                      icon: Icon(Icons.remove),
                      label: Text('Jama'),
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
                        'Pichli entry dobara: ${data!.last!.customerName}',
                      ),
                      subtitle: Text(
                        '${data.last!.txn.type == TxnType.udhaari ? 'Udhaari' : 'Jama'} '
                        '${formatPaise(data.last!.txn.amountPaise)} '
                        '(${methodLabel(data.last!.txn.method)}). Save se pehle check hoga.',
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
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search),
                    hintText: 'Customer khojein (naam, phone, ID)',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  _query.trim().isEmpty
                      ? 'Haal ke customers'
                      : 'Khoj ke nateeje',
                  style: theme.textTheme.titleMedium,
                ),
                if (snap.hasError)
                  Padding(
                    padding: const EdgeInsets.all(8),
                    child: Text('Load nahi hua: ${snap.error}'),
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
                          ? 'Abhi koi haal ka customer nahi. Upar khoj me naam likhein, '
                              'ya Customers tab me naya customer banayein.'
                          : 'Koi customer nahi mila.',
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
    );
  }
}
