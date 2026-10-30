import 'package:flutter/material.dart';

import 'app.dart';
import 'data/app_database.dart';
import 'services.dart';
import 'settings/app_settings.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    final db = await AppDatabase.open();
    final services = AppServices.fromDatabase(db);
    try {
      // Remove Trash items older than the retention period.
      await services.customers.purgeExpired();
    } catch (_) {
      // Cleanup is best-effort and must never block the app from opening.
    }
    final settings = await AppSettings.load();
    runApp(AuraXApp(services: services, settings: settings));
  } catch (e) {
    runApp(StartupErrorApp(error: e.toString()));
  }
}
