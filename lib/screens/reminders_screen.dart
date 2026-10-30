import 'package:flutter/material.dart';

import '../core/money.dart';
import '../data/reminder_repository.dart';
import '../services.dart';
import 'customer_detail_screen.dart';

String _dayText(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  return '${d.day.toString().padLeft(2, '0')}/'
      '${d.month.toString().padLeft(2, '0')}/${d.year}';
}

/// Customers whose due date is today or has passed and who still owe money.
/// Works without any notification permission.
class RemindersScreen extends StatelessWidget {
  const RemindersScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Reminders')),
      body: ListenableBuilder(
        listenable: services.bus,
        builder: (context, _) => FutureBuilder<List<ReminderItem>>(
          future: services.reminders.due(),
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
                  'Jinki wapsi ki tareekh aa gayi ya nikal gayi hai aur baaki hai. '
                  'App aapki taraf se kisi ko message nahi bhejta.',
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 12),
                if (data.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(child: Text('Abhi koi reminder nahi.')),
                  ),
                for (final r in data)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          InkWell(
                            onTap: () => Navigator.of(context).push(
                              MaterialPageRoute<void>(
                                builder: (_) => CustomerDetailScreen(
                                    customerId: r.customer.id),
                              ),
                            ),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(r.customer.name,
                                          style: theme.textTheme.titleMedium),
                                      Text(
                                        r.daysOverdue == 0
                                            ? 'Aaj wapsi ki tareekh hai'
                                            : '${r.daysOverdue} din se baaki '
                                                '(tareekh ${_dayText(r.dueDate)})',
                                        style: TextStyle(
                                          color: r.daysOverdue == 0
                                              ? null
                                              : theme.colorScheme.error,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                Text(
                                  formatPaise(r.balancePaise),
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Expanded(
                                child: PopupMenuButton<int>(
                                  tooltip: 'Snooze',
                                  onSelected: (days) => services.reminders
                                      .snooze(r.customer.id, days: days),
                                  itemBuilder: (_) => const [
                                    PopupMenuItem(value: 1, child: Text('1 din baad')),
                                    PopupMenuItem(value: 3, child: Text('3 din baad')),
                                    PopupMenuItem(value: 7, child: Text('7 din baad')),
                                  ],
                                  child: Container(
                                    height: 48,
                                    alignment: Alignment.center,
                                    decoration: BoxDecoration(
                                      border: Border.all(
                                          color: theme.colorScheme.outline),
                                      borderRadius: BorderRadius.circular(16),
                                    ),
                                    child: const Text('Snooze'),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: OutlinedButton(
                                  style: OutlinedButton.styleFrom(
                                    minimumSize: const Size(0, 48),
                                  ),
                                  onPressed: () =>
                                      services.reminders.dismiss(r.customer.id),
                                  child: const Text('Dismiss'),
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
