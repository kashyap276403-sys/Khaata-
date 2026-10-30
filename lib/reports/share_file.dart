import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

/// Shows a warning first, then hands the file to Android's share sheet so
/// the person chooses where it goes (Drive, WhatsApp, Files...). The app
/// itself never sends anything anywhere.
Future<void> confirmAndShareFile(
  BuildContext context, {
  required String fileName,
  required List<int> bytes,
  required String mimeType,
}) async {
  final messenger = ScaffoldMessenger.of(context);
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      icon: const Icon(Icons.privacy_tip_outlined),
      title: const Text('Personal jaankari'),
      content: const Text(
        'Is file me customers ke naam aur unka hisaab hai. '
        'Ise sirf unhi ke saath share karein jin par aapko bharosa ho.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
          onPressed: () => Navigator.pop(ctx, true),
          child: const Text('Aage badhein'),
        ),
      ],
    ),
  );
  if (ok != true) return;

  try {
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/$fileName');
    await file.writeAsBytes(bytes, flush: true);
    await Share.shareXFiles([XFile(file.path, mimeType: mimeType)]);
  } catch (e) {
    messenger.showSnackBar(
      SnackBar(content: Text('File ban/share nahi ho payi: $e')),
    );
  }
}
