import 'package:flutter/material.dart';

import 'screens/app_shell.dart';
import 'services.dart';
import 'settings/app_settings.dart';
import 'theme/app_theme.dart';

class AuraXApp extends StatefulWidget {
  const AuraXApp({super.key, required this.services, this.settings});

  final AppServices services;

  /// Look-and-feel preferences. Defaults to in-memory values (used by tests).
  final AppSettings? settings;

  @override
  State<AuraXApp> createState() => _AuraXAppState();
}

class _AuraXAppState extends State<AuraXApp> {
  late final AppSettings _settings = widget.settings ?? AppSettings.memory();

  @override
  Widget build(BuildContext context) {
    return ServicesScope(
      services: widget.services,
      child: SettingsScope(
        settings: _settings,
        child: ListenableBuilder(
          listenable: _settings,
          builder: (context, _) => MaterialApp(
            title: 'auraX Khaata',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.light(_settings.accent, _settings.glassAlpha),
            darkTheme: AppTheme.dark(_settings.accent, _settings.glassAlpha),
            themeMode: _settings.themeMode,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: TextScaler.linear(_settings.fontScale),
              ),
              child: child ?? const SizedBox.shrink(),
            ),
            home: const AppShell(),
          ),
        ),
      ),
    );
  }
}

/// Makes [AppSettings] available to the Settings screen.
class SettingsScope extends InheritedWidget {
  const SettingsScope(
      {super.key, required this.settings, required super.child});

  final AppSettings settings;

  static AppSettings of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<SettingsScope>();
    assert(scope != null, 'SettingsScope missing above this widget');
    return scope!.settings;
  }

  @override
  bool updateShouldNotify(SettingsScope oldWidget) =>
      settings != oldWidget.settings;
}

/// Shown if the local database cannot be opened. Data is not touched.
class StartupErrorApp extends StatelessWidget {
  const StartupErrorApp({super.key, required this.error});

  final String error;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: AppTheme.light(),
      home: Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 64),
                const SizedBox(height: 16),
                const Text(
                  'Database khul nahi paya. Aapka data delete nahi hua hai.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 12),
                Text(error, textAlign: TextAlign.center),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
