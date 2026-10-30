import 'package:flutter/material.dart';

import '../core/money.dart';
import '../data/customer_repository.dart' show trashRetentionDays;
import '../data/models.dart';
import '../services.dart';
import '../widgets/entry_sheet.dart';
import '../widgets/txn_tile.dart';
import 'customer_form_screen.dart';
import 'reports_screen.dart' show ReportWindow;
import 'statement_screen.dart';

class _Detail {
  const _Detail(this.customer, this.balancePaise, this.txns);
  final Customer? customer;
  final int balancePaise;
  final List<LedgerTxn> txns;
}

class CustomerDetailScreen extends StatelessWidget {
  const CustomerDetailScreen({super.key, required this.customerId});

  final String customerId;

  Future<_Detail> _load(AppServices s) async {
    final c = await s.customers.getById(customerId);
    if (c == null) return const _Detail(null, 0, []);
    final bal = await s.customers.balanceFor(customerId);
    final txns = await s.ledger.transactionsFor(customerId);
    return _Detail(c, bal, txns);
  }

  Future<void> _reverse(BuildContext context, LedgerTxn t) async {
    final services = ServicesScope.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Galti sudharein?'),
        content: Text(
          'Is entry (${formatPaise(t.amountPaise)}) ko mitaya nahi jayega. '
          'Ek ulti entry judegi aur purana record history me rahega.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Reverse karein'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await services.ledger.reverse(t.id);
      messenger.showSnackBar(const SnackBar(content: Text('Reverse ho gaya')));
    } on LedgerException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _delete(BuildContext context, Customer c) async {
    final services = ServicesScope.of(context);
    final navigator = Navigator.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Customer delete karein?'),
        content: Text(
          '${c.name} aur uska poora hisaab Trash me chala jayega. '
          '$trashRetentionDays din tak wahan se wapas la sakte hain.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await services.customers.softDelete(c.id);
    navigator.pop();
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: services.bus,
      builder: (context, _) {
        return FutureBuilder<_Detail>(
          future: _load(services),
          builder: (context, snap) {
            final d = snap.data;
            if (snap.hasError) {
              return Scaffold(
                appBar: AppBar(),
                body: Center(child: Text('Load nahi hua: ${snap.error}')),
              );
            }
            if (d == null) {
              return Scaffold(
                appBar: AppBar(),
                body: const Center(child: CircularProgressIndicator()),
              );
            }
            final c = d.customer;
            if (c == null) {
              return Scaffold(
                appBar: AppBar(),
                body: const Center(child: Text('Customer nahi mila')),
              );
            }

            final reversed = {
              for (final t in d.txns)
                if (t.reversesId != null) t.reversesId!,
            };
            final bal = d.balancePaise;
            final String balLabel;
            final Color balColor;
            if (bal > 0) {
              balLabel = 'Baaki (aapko lena hai)';
              balColor = theme.colorScheme.error;
            } else if (bal < 0) {
              balLabel = 'Advance (aapko dena hai)';
              balColor = theme.colorScheme.primary;
            } else {
              balLabel = 'Hisaab barabar';
              balColor = theme.colorScheme.onSurfaceVariant;
            }

            return Scaffold(
              appBar: AppBar(
                title: Text(c.name),
                actions: [
                  PopupMenuButton<String>(
                    onSelected: (v) async {
                      if (v == 'edit') {
                        await Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => CustomerFormScreen(existing: c),
                          ),
                        );
                      } else if (v == 'statement') {
                        const far = 4102444800000; // year 2100
                        await Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => StatementScreen(
                              customerId: c.id,
                              window: const ReportWindow(0, far, 'Poora hisaab'),
                            ),
                          ),
                        );
                      } else if (v == 'archive') {
                        final nav = Navigator.of(context);
                        await services.customers.setArchived(c.id, true);
                        nav.pop();
                      } else if (v == 'delete') {
                        await _delete(context, c);
                      }
                    },
                    itemBuilder: (_) => const [
                      PopupMenuItem(value: 'edit', child: Text('Edit')),
                      PopupMenuItem(
                          value: 'statement', child: Text('Statement')),
                      PopupMenuItem(value: 'archive', child: Text('Archive')),
                      PopupMenuItem(value: 'delete', child: Text('Delete')),
                    ],
                  ),
                ],
              ),
              body: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            [c.code, if (c.phone != null) c.phone!].join(' · '),
                            style: theme.textTheme.bodyMedium,
                          ),
                          if (c.address != null) Text(c.address!),
                          if (c.notes != null) Text(c.notes!),
                          const SizedBox(height: 8),
                          Text(balLabel, style: theme.textTheme.bodyMedium),
                          FittedBox(
                            fit: BoxFit.scaleDown,
                            alignment: Alignment.centerLeft,
                            child: Text(
                              formatPaise(bal.abs()),
                              style: theme.textTheme.displaySmall?.copyWith(
                                color: balColor,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  FilledButton.icon(
                    onPressed: () => showEntrySheet(
                      context,
                      customerId: c.id,
                      customerName: c.name,
                      type: TxnType.udhaari,
                    ),
                    icon: const Icon(Icons.add),
                    label: const Text('Udhaari'),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.tonalIcon(
                    onPressed: () => showEntrySheet(
                      context,
                      customerId: c.id,
                      customerName: c.name,
                      type: TxnType.jama,
                    ),
                    icon: const Icon(Icons.remove),
                    label: const Text('Jama'),
                  ),
                  const SizedBox(height: 24),
                  Text('Hisaab ki history', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  if (d.txns.isEmpty)
                    Text(
                      'Abhi koi entry nahi.',
                      style: theme.textTheme.bodyMedium,
                    ),
                  for (final t in d.txns)
                    TxnTile(
                      txn: t,
                      onReverse: (t.isReversal || reversed.contains(t.id))
                          ? null
                          : () => _reverse(context, t),
                    ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}
