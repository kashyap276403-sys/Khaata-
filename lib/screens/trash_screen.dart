import 'package:flutter/material.dart';

import '../core/money.dart';
import '../data/customer_repository.dart' show trashRetentionDays;
import '../data/models.dart';
import '../services.dart';

class TrashScreen extends StatelessWidget {
  const TrashScreen({super.key});

  int _daysLeft(Customer c) {
    final deleted = DateTime.fromMillisecondsSinceEpoch(c.deletedAt!);
    final expires = deleted.add(const Duration(days: trashRetentionDays));
    final left = expires.difference(DateTime.now()).inDays;
    return left < 0 ? 0 : left;
  }

  Future<void> _restore(BuildContext context, Customer c) async {
    final services = ServicesScope.of(context);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await services.customers.restore(c.id);
      messenger.showSnackBar(
        SnackBar(content: Text('${c.name} wapas aa gaya')),
      );
    } on LedgerException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _purge(BuildContext context, Customer c) async {
    final services = ServicesScope.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.warning_amber_rounded),
        title: const Text('Hamesha ke liye delete?'),
        content: Text(
          '${c.name} aur uski saari entries is phone se pakke taur par '
          'hat jayengi. Yeh wapas nahi aa sakta.\n\n'
          'Agar aapne pehle Google Drive backup liya tha, to purane backup '
          'me yeh data tab tak reh sakta hai jab tak wo backup khud expire na ho.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Hamesha ke liye delete'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await services.customers.purge(c.id);
      messenger.showSnackBar(const SnackBar(content: Text('Delete ho gaya')));
    } on LedgerException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Trash')),
      body: ListenableBuilder(
        listenable: services.bus,
        builder: (context, _) => FutureBuilder<List<CustomerWithBalance>>(
          future: services.customers.listDeleted(),
          builder: (context, snap) {
            if (snap.hasError) {
              return Center(child: Text('Load nahi hua: ${snap.error}'));
            }
            final data = snap.data;
            if (data == null) {
              return const Center(child: CircularProgressIndicator());
            }
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Delete kiye hue customers $trashRetentionDays din tak yahan '
                  'rehte hain, phir apne aap hamesha ke liye hat jate hain.',
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 12),
                if (data.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(child: Text('Trash khaali hai.')),
                  ),
                for (final item in data)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(item.customer.name,
                              style: theme.textTheme.titleMedium),
                          Text(
                            '${item.customer.code} · hisaab '
                            '${formatPaise(item.balancePaise)} · '
                            '${_daysLeft(item.customer)} din bache',
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Expanded(
                                child: FilledButton.tonal(
                                  style: FilledButton.styleFrom(
                                    minimumSize: const Size(0, 48),
                                  ),
                                  onPressed: () => _restore(context, item.customer),
                                  child: const Text('Wapas laayein'),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: OutlinedButton(
                                  style: OutlinedButton.styleFrom(
                                    minimumSize: const Size(0, 48),
                                    foregroundColor: theme.colorScheme.error,
                                  ),
                                  onPressed: () => _purge(context, item.customer),
                                  child: const Text('Hamesha delete'),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
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
