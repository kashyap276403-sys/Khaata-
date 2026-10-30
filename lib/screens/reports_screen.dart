import 'dart:convert';

import 'package:flutter/material.dart';

import '../core/money.dart';
import '../data/report_repository.dart';
import '../reports/exporter.dart';
import '../reports/share_file.dart';
import '../services.dart';
import 'statement_screen.dart';

enum _Period { today, week, month, custom }

String _d(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';

/// Period as a half-open window [from, to) plus a readable label.
class ReportWindow {
  const ReportWindow(this.fromMs, this.toMs, this.label);
  final int fromMs;
  final int toMs;
  final String label;
}

class ReportsScreen extends StatefulWidget {
  const ReportsScreen({super.key});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  _Period _period = _Period.today;
  DateTimeRange? _custom;

  ReportWindow _window() {
    final n = DateTime.now();
    final today = DateTime(n.year, n.month, n.day);
    final tomorrow = DateTime(n.year, n.month, n.day + 1);
    switch (_period) {
      case _Period.today:
        return ReportWindow(today.millisecondsSinceEpoch,
            tomorrow.millisecondsSinceEpoch, 'Aaj (${_d(today)})');
      case _Period.week:
        final from = DateTime(n.year, n.month, n.day - 6);
        return ReportWindow(from.millisecondsSinceEpoch,
            tomorrow.millisecondsSinceEpoch, 'Pichhle 7 din (${_d(from)} - ${_d(today)})');
      case _Period.month:
        final from = DateTime(n.year, n.month, 1);
        final next = DateTime(n.year, n.month + 1, 1);
        return ReportWindow(from.millisecondsSinceEpoch,
            next.millisecondsSinceEpoch, 'Is mahine (${_d(from)} se)');
      case _Period.custom:
        final r = _custom ?? DateTimeRange(start: today, end: today);
        final end = DateTime(r.end.year, r.end.month, r.end.day + 1);
        return ReportWindow(r.start.millisecondsSinceEpoch,
            end.millisecondsSinceEpoch, '${_d(r.start)} - ${_d(r.end)}');
    }
  }

  Future<void> _pickCustom() async {
    final n = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(n.year + 1, 12, 31),
      initialDateRange: _custom,
    );
    if (picked != null) {
      setState(() {
        _custom = picked;
        _period = _Period.custom;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);
    final w = _window();
    const labels = {
      _Period.today: 'Aaj',
      _Period.week: '7 din',
      _Period.month: 'Is mahine',
      _Period.custom: 'Apni tareekh',
    };

    return ListenableBuilder(
      listenable: services.bus,
      builder: (context, _) => FutureBuilder<ReportSummary>(
        future: services.reports.summary(fromMs: w.fromMs, toMs: w.toMs),
        builder: (context, snap) {
          final s = snap.data;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text('Reports', style: theme.textTheme.headlineMedium),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  for (final p in _Period.values)
                    ChoiceChip(
                      label: Text(labels[p]!),
                      selected: _period == p,
                      onSelected: (_) {
                        if (p == _Period.custom) {
                          _pickCustom();
                        } else {
                          setState(() => _period = p);
                        }
                      },
                    ),
                ],
              ),
              const SizedBox(height: 8),
              Text(w.label, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 12),
              if (snap.hasError)
                Text('Report nahi ban payi: ${snap.error}',
                    style: TextStyle(color: theme.colorScheme.error))
              else if (s == null)
                const Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                )
              else ...[
                _Tile(label: 'Total Udhaari', value: formatPaise(s.udhaariPaise)),
                _Tile(label: 'Total Jama', value: formatPaise(s.jamaPaise)),
                _Tile(
                  label: 'Is dauraan baaki me badlaav',
                  value: formatPaise(s.netPaise),
                ),
                _Tile(
                  label: 'Period ke ant me total baaki',
                  value: formatPaise(s.closingOutstandingPaise),
                  big: true,
                ),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Jama kaise mila', style: theme.textTheme.titleSmall),
                        const SizedBox(height: 6),
                        Text('Cash: ${formatPaise(s.jamaCashPaise)}'),
                        Text('UPI: ${formatPaise(s.jamaUpiPaise)}'),
                        Text('Other: ${formatPaise(s.jamaOtherPaise)}'),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${s.entryCount} entries, jinme ${s.reversalCount} reversal. '
                  'Reversal bhi totals me judte hain, taaki report khaate ke '
                  'baaki se hamesha match kare.',
                  style: theme.textTheme.bodySmall,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.tonalIcon(
                        style: FilledButton.styleFrom(
                            minimumSize: const Size(0, 52)),
                        onPressed: () => confirmAndShareFile(
                          context,
                          fileName: 'khaata_report.csv',
                          bytes: csvBytes(buildReportCsv(s, w.label)),
                          mimeType: 'text/csv',
                        ),
                        icon: const Icon(Icons.table_chart_outlined),
                        label: const Text('CSV'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton.tonalIcon(
                        style: FilledButton.styleFrom(
                            minimumSize: const Size(0, 52)),
                        onPressed: () async {
                          final bytes = await buildReportPdf(s, w.label);
                          if (!context.mounted) return;
                          await confirmAndShareFile(
                            context,
                            fileName: 'khaata_report.pdf',
                            bytes: bytes,
                            mimeType: 'application/pdf',
                          );
                        },
                        icon: const Icon(Icons.picture_as_pdf_outlined),
                        label: const Text('PDF'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                Text('Customer-wise (statement ke liye dabayein)',
                    style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                if (s.rows.isEmpty)
                  const Text('Is period me koi entry nahi.'),
                for (final r in s.rows)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(r.name),
                    subtitle: Text(
                      '${r.code} · Udhaari ${formatPaise(r.udhaariPaise)} '
                      '· Jama ${formatPaise(r.jamaPaise)}',
                    ),
                    trailing: Text(
                      formatPaise(r.closingBalancePaise),
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => StatementScreen(
                          customerId: r.customerId,
                          window: w,
                        ),
                      ),
                    ),
                  ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile({required this.label, required this.value, this.big = false});

  final String label;
  final String value;
  final bool big;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: theme.textTheme.bodyMedium),
            const SizedBox(height: 4),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(
                value,
                style: (big
                        ? theme.textTheme.headlineMedium
                        : theme.textTheme.titleLarge)
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
