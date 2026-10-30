import 'package:flutter/material.dart';

import '../app.dart' show SettingsScope;
import '../settings/app_settings.dart';
import 'history_screen.dart';
import 'reminders_screen.dart';
import 'trash_screen.dart';

/// Settings hub. Language, notifications, backup etc. arrive in later phases.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final settings = SettingsScope.of(context);

    return ListenableBuilder(
      listenable: settings,
      builder: (context, _) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Settings', style: theme.textTheme.headlineMedium),
          const SizedBox(height: 12),
          ListTile(
            leading: const Icon(Icons.notifications_active_outlined),
            title: const Text('Reminders'),
            subtitle: const Text('Wapsi ki tareekh wale customers'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => const RemindersScreen()),
            ),
          ),
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
          Text('Dikhawat', style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          Text('Theme', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 4),
          SegmentedButton<ThemeMode>(
            segments: const [
              ButtonSegment(
                value: ThemeMode.system,
                icon: Icon(Icons.brightness_auto),
                label: Text('Auto'),
              ),
              ButtonSegment(
                value: ThemeMode.light,
                icon: Icon(Icons.light_mode_outlined),
                label: Text('Light'),
              ),
              ButtonSegment(
                value: ThemeMode.dark,
                icon: Icon(Icons.dark_mode_outlined),
                label: Text('Dark'),
              ),
            ],
            selected: {settings.themeMode},
            onSelectionChanged: (s) => settings.setThemeMode(s.first),
          ),
          const SizedBox(height: 16),
          Text('Rang', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              for (var i = 0; i < AppSettings.accentColors.length; i++)
                Semantics(
                  label: AppSettings.accentNames[i],
                  selected: settings.accentIndex == i,
                  button: true,
                  child: InkWell(
                    customBorder: const CircleBorder(),
                    onTap: () => settings.setAccent(i),
                    child: Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppSettings.accentColors[i],
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: settings.accentIndex == i
                              ? theme.colorScheme.onSurface
                              : Colors.transparent,
                          width: 3,
                        ),
                      ),
                      child: settings.accentIndex == i
                          ? const Icon(Icons.check, color: Colors.white)
                          : null,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Text('Akshar ka size', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 4),
          SegmentedButton<int>(
            segments: [
              for (var i = 0; i < AppSettings.fontNames.length; i++)
                ButtonSegment(
                  value: i,
                  label: Text(AppSettings.fontNames[i]),
                ),
            ],
            selected: {settings.fontIndex},
            onSelectionChanged: (s) => settings.setFontIndex(s.first),
          ),
          const Divider(height: 32),
          const ListTile(
            leading: Icon(Icons.info_outline),
            title: Text('Aane wale settings'),
            subtitle: Text(
              'Language, notifications, backup, export aur privacy '
              'agle phases me aayenge.',
            ),
          ),
        ],
      ),
    );
  }
}
