import 'package:flutter/material.dart';

import '../core/money.dart';
import '../core/text_utils.dart';
import '../data/report_repository.dart';
import '../reports/exporter.dart';
import '../reports/share_file.dart';
import '../services.dart';
import '../widgets/txn_tile.dart';
import 'reports_screen.dart' show ReportWindow;

/// One customer's statement for a period: opening balance, every entry
/// with a running balance, closing balance.
class StatementScreen extends StatelessWidget {
  const StatementScreen({
    super.key,
    required this.customerId,
    required this.window,
  });

  final String customerId;
  final ReportWindow window;

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return FutureBuilder<CustomerStatement?>(
      future: services.reports.statement(
        customerId: customerId,
        fromMs: window.fromMs,
        toMs: window.toMs,
      ),
      builder: (context, snap) {
        if (snap.hasError) {
          return Scaffold(
            appBar: AppBar(title: const Text('Statement')),
            body: Center(child: Text('Load nahi hua: ${snap.error}')),
          );
        }
        if (snap.connectionState != ConnectionState.done) {
          return Scaffold(
            appBar: AppBar(title: const Text('Statement')),
            body: const Center(child: CircularProgressIndicator()),
          );
        }
        final st = snap.data;
        if (st == null) {
          return Scaffold(
            appBar: AppBar(title: const Text('Statement')),
            body: const Center(child: Text('Customer nahi mila')),
          );
        }
        final safeName = st.customer.code;

        return Scaffold(
          appBar: AppBar(title: Text('Statement: ${st.customer.name}')),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(window.label, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 8),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Shuruaati baaki: ${formatPaise(st.openingPaise)}'),
                      const SizedBox(height: 4),
                      Text(
                        'Ant me baaki: ${formatPaise(st.closingPaise)}',
                        style: theme.textTheme.titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.tonalIcon(
                      style:
                          FilledButton.styleFrom(minimumSize: const Size(0, 52)),
                      onPressed: () => confirmAndShareFile(
                        context,
                        fileName: 'statement_$safeName.csv',
                        bytes: csvBytes(buildStatementCsv(st, window.label)),
                        mimeType: 'text/csv',
                      ),
                      icon: const Icon(Icons.table_chart_outlined),
                      label: const Text('CSV'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton.tonalIcon(
                      style:
                          FilledButton.styleFrom(minimumSize: const Size(0, 52)),
                      onPressed: () async {
                        final bytes =
                            await buildStatementPdf(st, window.label);
                        if (!context.mounted) return;
                        await confirmAndShareFile(
                          context,
                          fileName: 'statement_$safeName.pdf',
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
              const SizedBox(height: 16),
              if (st.lines.isEmpty)
                const Text('Is period me is customer ki koi entry nahi.'),
              for (final l in st.lines)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    TxnTile(txn: l.txn),
                    Padding(
                      padding: const EdgeInsets.only(left: 56, bottom: 4),
                      child: Text(
                        'Baaki: ${formatPaise(l.runningBalancePaise)}  '
                        '·  ${formatDateTime(l.txn.createdAt)}',
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],
                ),
            ],
          ),
        );
      },
    );
  }
}
