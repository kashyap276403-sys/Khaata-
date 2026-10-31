import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../app.dart' show SettingsScope;
import '../reports/exporter.dart';
import '../reports/share_file.dart';
import '../services.dart';
import '../settings/app_settings.dart';
import 'history_screen.dart';
import 'privacy_screen.dart';
import 'reminders_screen.dart';
import 'trash_screen.dart';

/// Settings hub. Language, notifications and Google backup arrive later.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  /// Version text; never throws (the plugin may be unavailable, e.g. in tests).
  static Future<String> _versionText() async {
    try {
      final i = await PackageInfo.fromPlatform();
      return 'Version ${i.version} (build ${i.buildNumber})';
    } catch (_) {
      return 'Version pata nahi chala';
    }
  }

  Future<void> _checkLedger(BuildContext context) async {
    final services = ServicesScope.of(context);
    final issues = await services.ledger.integrityIssues();
    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: Icon(issues.isEmpty
            ? Icons.verified_outlined
            : Icons.warning_amber_rounded),
        title: Text(issues.isEmpty ? 'Sab theek hai' : 'Problem mili'),
        content: SingleChildScrollView(
          child: Text(issues.isEmpty
              ? 'Hisaab ki jaanch me koi gadbad nahi mili.'
              : issues.join('\n\n')),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Theek hai'),
          ),
        ],
      ),
    );
  }

  Future<void> _exportJson(BuildContext context) async {
    final services = ServicesScope.of(context);
    final json = await services.exports.exportJson();
    if (!context.mounted) return;
    await confirmAndShareFile(
      context,
      fileName: 'khaata_full_export.json',
      bytes: utf8Bytes(json),
      mimeType: 'application/json',
    );
  }

  Future<void> _exportLedgerCsv(BuildContext context) async {
    final services = ServicesScope.of(context);
    final rows = await services.exports.ledgerRows();
    if (!context.mounted) return;
    await confirmAndShareFile(
      context,
      fileName: 'khaata_full_ledger.csv',
      bytes: csvBytes(buildLedgerCsv(rows)),
      mimeType: 'text/csv',
    );
  }

  Future<void> _eraseAll(BuildContext context) async {
    final services = ServicesScope.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final controller = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          icon: const Icon(Icons.warning_amber_rounded),
          title: const Text('Poora data delete?'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Saare customers, entries aur history is phone se hamesha ke '
                'liye hat jayenge. Yeh wapas nahi aa sakta.\n\n'
                'Pehle "Poora data download" karke apni copy bacha lein.\n\n'
                'Aage badhne ke liye neeche DELETE likhein.',
              ),
              const SizedBox(height: 12),
              TextField(
                controller: controller,
                autofocus: true,
                textCapitalization: TextCapitalization.characters,
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  hintText: 'DELETE',
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
              onPressed: controller.text.trim() == 'DELETE'
                  ? () => Navigator.pop(ctx, true)
                  : null,
              child: const Text('Hamesha ke liye delete'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    if (ok != true) return;
    try {
      final r = await services.exports.eraseAll();
      messenger.showSnackBar(
        SnackBar(
          content: Text(
              '${r.customers} customers aur ${r.transactions} entries delete ho gayi'),
        ),
      );
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Delete nahi ho paya: $e')));
    }
  }

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
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Simple mode'),
            subtitle: const Text(
              'Kam figures aur aasaan screen. Band karne par Pro mode: poori detail.',
            ),
            value: !settings.proMode,
            onChanged: (v) => settings.setProMode(!v),
          ),
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
          const SizedBox(height: 16),
          Text('Glass effect (cards ka rang)', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 4),
          SegmentedButton<int>(
            segments: [
              for (var i = 0; i < AppSettings.glassNames.length; i++)
                ButtonSegment(
                  value: i,
                  label: Text(AppSettings.glassNames[i]),
                ),
            ],
            selected: {settings.glassIndex},
            onSelectionChanged: (s) => settings.setGlassIndex(s.first),
          ),
          const Divider(height: 32),
          Text('Data', style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          ListTile(
            leading: const Icon(Icons.download_outlined),
            title: const Text('Poora data download (JSON)'),
            subtitle: const Text('Saare customers, entries aur history ki copy'),
            onTap: () => _exportJson(context),
          ),
          ListTile(
            leading: const Icon(Icons.table_chart_outlined),
            title: const Text('Poora ledger (CSV)'),
            subtitle: const Text('Excel / Sheets me khulne wali list'),
            onTap: () => _exportLedgerCsv(context),
          ),
          if (settings.proMode)
            ListTile(
              leading: const Icon(Icons.fact_check_outlined),
              title: const Text('Hisaab ki jaanch'),
              subtitle: const Text('Ledger me koi gadbad to nahi, check karein'),
              onTap: () => _checkLedger(context),
            ),
          ListTile(
            leading: Icon(Icons.delete_forever_outlined,
                color: theme.colorScheme.error),
            title: Text('Poora data delete karein',
                style: TextStyle(color: theme.colorScheme.error)),
            subtitle: const Text('Is phone se sab kuch hamesha ke liye hat jayega'),
            onTap: () => _eraseAll(context),
          ),
          const Divider(height: 32),
          ListTile(
            leading: const Icon(Icons.privacy_tip_outlined),
            title: const Text('Privacy'),
            subtitle: const Text('Aapka data kahan aur kaise rehta hai'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => const PrivacyScreen()),
            ),
          ),
          FutureBuilder<String>(
            future: _versionText(),
            builder: (context, snap) => ListTile(
              leading: const Icon(Icons.info_outline),
              title: const Text('auraX Khaata'),
              subtitle: Text(snap.data ?? 'Version load ho raha hai...'),
            ),
          ),
          const ListTile(
            leading: Icon(Icons.hourglass_empty),
            title: Text('Aane wale'),
            subtitle: Text(
              'Language, notifications, voice entry aur Google backup '
              'agle phases me aayenge.',
            ),
          ),
        ],
      ),
    );
  }
}
