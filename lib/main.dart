import 'package:flutter/material.dart';

import 'app.dart';
import 'data/app_database.dart';
import 'services.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    final db = await AppDatabase.open();
    runApp(AuraXApp(services: AppServices.fromDatabase(db)));
  } catch (e) {
    runApp(StartupErrorApp(error: e.toString()));
  }
}
