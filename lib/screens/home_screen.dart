import 'package:flutter/material.dart';

import '../core/money.dart';
import '../data/models.dart';
import '../services.dart';
import '../widgets/txn_tile.dart';

/// Home dashboard. All numbers come from the SQLite ledger.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  Future<(DashboardSummary, List<RecentTxn>)> _load(AppServices s) async {
    final summary = await s.ledger.dashboard();
    final recent = await s.ledger.recent(limit: 5);
    return (summary, recent);
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: services.bus,
      builder: (context, _) {
        return FutureBuilder<(DashboardSummary, List<RecentTxn>)>(
          future: _load(services),
          builder: (context, snap) {
            final summary = snap.data?.$1 ?? DashboardSummary.empty;
            final recent = snap.data?.$2 ?? const <RecentTxn>[];
            final loading = snap.connectionState != ConnectionState.done &&
                snap.data == null;

            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text('auraX Khaata', style: theme.textTheme.headlineMedium),
                const SizedBox(height: 4),
                Text(
                  'Namaste! Aapka digital khaata.',
                  style: theme.textTheme.bodyLarge,
                ),
                const SizedBox(height: 12),
                if (loading) const LinearProgressIndicator(),
                if (snap.hasError)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Text(
                      'Data load nahi ho paya: ${snap.error}',
                      style: TextStyle(color: theme.colorScheme.error),
                    ),
                  ),
                const SizedBox(height: 8),
                _StatCard(
                  label: 'Total baaki (Outstanding)',
                  value: formatPaise(summary.outstandingPaise),
                  large: true,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: _StatCard(
                        label: 'Aaj ka udhaari',
                        value: formatPaise(summary.todayUdhaariPaise),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _StatCard(
                        label: 'Aaj ka jama',
                        value: formatPaise(summary.todayJamaPaise),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: _StatCard(
                        label: 'Total customers',
                        value: '${summary.customerCount}',
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _StatCard(
                        label: 'Baaki wale',
                        value: '${summary.pendingCount}',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                Text('Recent transactions', style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                if (!loading && recent.isEmpty)
                  Text(
                    'Abhi koi transaction nahi. Customers tab me customer '
                    'chunkar Udhaari ya Jama likhein.',
                    style: theme.textTheme.bodyMedium,
                  ),
                for (final r in recent)
                  TxnTile(txn: r.txn, customerName: r.customerName),
              ],
            );
          },
        );
      },
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({
    required this.label,
    required this.value,
    this.large = false,
  });

  final String label;
  final String value;
  final bool large;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: theme.textTheme.bodyMedium),
            const SizedBox(height: 6),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(
                value,
                style: (large
                        ? theme.textTheme.displaySmall
                        : theme.textTheme.headlineSmall)
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
