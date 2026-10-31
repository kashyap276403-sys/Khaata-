import 'package:flutter/material.dart';

import '../core/money.dart';
import '../app.dart' show SettingsScope;
import '../data/models.dart';
import '../services.dart';
import '../widgets/integrity_banner.dart';
import '../widgets/txn_tile.dart';
import 'history_screen.dart';
import 'quick_entry_screen.dart';
import 'reminders_screen.dart';
import 'trash_screen.dart';

/// Home dashboard. All numbers come from the SQLite ledger.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  Future<(DashboardSummary, List<RecentTxn>, int)> _load(AppServices s) async {
    final summary = await s.ledger.dashboard();
    final recent = await s.ledger.recent(limit: 5);
    final due = await s.reminders.due();
    return (summary, recent, due.length);
  }

  void _quick(BuildContext context, TxnType type) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => QuickEntryScreen(initialType: type, showAppBar: true),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final settings = SettingsScope.of(context);
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: Listenable.merge([services.bus, settings]),
      builder: (context, _) {
        return FutureBuilder<(DashboardSummary, List<RecentTxn>, int)>(
          future: _load(services),
          builder: (context, snap) {
            final summary = snap.data?.$1 ?? DashboardSummary.empty;
            final recent = snap.data?.$2 ?? const <RecentTxn>[];
            final dueCount = snap.data?.$3 ?? 0;
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
                const IntegrityBanner(),
                if (dueCount > 0) ...[
                  Card(
                    color: Theme.of(context).colorScheme.errorContainer,
                    child: ListTile(
                      leading: const Icon(Icons.notifications_active_outlined),
                      title: Text(
                          '$dueCount customer ki wapsi ki tareekh aa gayi'),
                      subtitle: const Text('Dekhne ke liye dabayein'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const RemindersScreen(),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
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
                if (settings.proMode) ...[
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
                ],
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: () => _quick(context, TxnType.udhaari),
                  icon: const Icon(Icons.add),
                  label: const Text('Udhaari'),
                ),
                const SizedBox(height: 12),
                FilledButton.tonalIcon(
                  onPressed: () => _quick(context, TxnType.jama),
                  icon: const Icon(Icons.remove),
                  label: const Text('Jama'),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => const HistoryScreen(),
                          ),
                        ),
                        icon: const Icon(Icons.history),
                        label: const Text('History'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => const TrashScreen(),
                          ),
                        ),
                        icon: const Icon(Icons.delete_outline),
                        label: const Text('Trash'),
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
