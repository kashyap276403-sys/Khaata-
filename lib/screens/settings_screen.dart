import 'package:flutter/material.dart';

import 'history_screen.dart';
import 'trash_screen.dart';

/// Settings hub. Language, theme, backup etc. arrive in later phases.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Settings', style: theme.textTheme.headlineMedium),
        const SizedBox(height: 12),
        ListTile(
          leading: const Icon(Icons.history),
          title: const Text('History'),
          subtitle: const Text('Saari entries aur activity'),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const HistoryScreen()),
          ),
        ),
        ListTile(
          leading: const Icon(Icons.delete_outline),
          title: const Text('Trash'),
          subtitle: const Text('Delete kiye hue customers'),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const TrashScreen()),
          ),
        ),
        const Divider(),
        const ListTile(
          leading: Icon(Icons.info_outline),
          title: Text('Aane wale settings'),
          subtitle: Text(
            'Language, theme, font, notifications, backup, export aur '
            'privacy agle phases me aayenge.',
          ),
        ),
      ],
    );
  }
}
