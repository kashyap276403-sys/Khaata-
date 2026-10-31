import 'package:flutter/material.dart';

import '../services.dart';

/// Shows a warning on Home only when the ledger consistency check finds a
/// problem. Nothing is shown when everything is fine.
class IntegrityBanner extends StatelessWidget {
  const IntegrityBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    return FutureBuilder<List<String>>(
      future: services.ledger.integrityIssues(),
      builder: (context, snap) {
        final issues = snap.data;
        if (issues == null || issues.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Card(
            color: Theme.of(context).colorScheme.errorContainer,
            child: ListTile(
              leading: const Icon(Icons.warning_amber_rounded),
              title: const Text('Hisaab me gadbad mili'),
              subtitle: Text(
                '${issues.length} problem mili. Kuch bhi delete na karein; '
                'Settings > Data > "Hisaab ki jaanch" me details dekhein.',
              ),
            ),
          ),
        );
      },
    );
  }
}
