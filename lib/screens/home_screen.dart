import 'package:flutter/material.dart';

import '../core/money.dart';

/// Home dashboard. Values are zero placeholders until the ledger
/// (SQLite) is implemented in Phase 3.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  void _comingSoon(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Yeh feature agle phase me aayega.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('auraX Khaata', style: theme.textTheme.headlineMedium),
        const SizedBox(height: 4),
        Text('Namaste! Aapka digital khaata.', style: theme.textTheme.bodyLarge),
        const SizedBox(height: 16),
        _StatCard(
          label: 'Total baaki (Outstanding)',
          value: formatPaise(0),
          large: true,
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _StatCard(label: 'Aaj ka udhaari', value: formatPaise(0)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _StatCard(label: 'Aaj ka jama', value: formatPaise(0)),
            ),
          ],
        ),
        const SizedBox(height: 12),
        const _StatCard(label: 'Total customers', value: '0'),
        const SizedBox(height: 20),
        FilledButton.icon(
          onPressed: () => _comingSoon(context),
          icon: const Icon(Icons.add),
          label: const Text('Udhaari'),
        ),
        const SizedBox(height: 12),
        FilledButton.tonalIcon(
          onPressed: () => _comingSoon(context),
          icon: const Icon(Icons.remove),
          label: const Text('Jama'),
        ),
        const SizedBox(height: 24),
        Text('Recent transactions', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        Text(
          'Abhi koi transaction nahi.',
          style: theme.textTheme.bodyMedium,
        ),
      ],
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
            Text(
              value,
              style: (large
                      ? theme.textTheme.displaySmall
                      : theme.textTheme.headlineSmall)
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
          ],
        ),
      ),
    );
  }
}
