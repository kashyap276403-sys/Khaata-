import 'package:flutter/material.dart';

class CustomersScreen extends StatelessWidget {
  const CustomersScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.people, size: 64, color: theme.colorScheme.primary),
            const SizedBox(height: 16),
            Text('Abhi koi customer nahi', style: theme.textTheme.headlineSmall),
            const SizedBox(height: 8),
            Text(
              'Customer add karne ka feature Phase 3 me aayega.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyLarge,
            ),
          ],
        ),
      ),
    );
  }
}
