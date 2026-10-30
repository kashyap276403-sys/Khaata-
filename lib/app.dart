import 'package:flutter/material.dart';

import 'screens/app_shell.dart';
import 'theme/app_theme.dart';

class AuraXApp extends StatelessWidget {
  const AuraXApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'auraX Khaata',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ThemeMode.system,
      home: const AppShell(),
    );
  }
}
