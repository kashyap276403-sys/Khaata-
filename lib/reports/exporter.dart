import 'dart:convert';
import 'dart:typed_data';

import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../core/money.dart';
import '../core/text_utils.dart';
import '../data/models.dart';
import '../data/report_repository.dart';

/// Plain "50.50" style rupees for spreadsheets (always 2 decimals, no
/// symbol, integer maths only).
String rupeesPlain(int paise) {
  final neg = paise < 0;
  final a = paise.abs();
  return '${neg ? '-' : ''}${a ~/ 100}.${(a % 100).toString().padLeft(2, '0')}';
}

/// One CSV cell. Quotes when needed and neutralises spreadsheet formulas
/// (cells starting with = + - @ would otherwise run as formulas when a
/// customer name or note is opened in Excel/Sheets).
String csvCell(String? v) {
  var s = v ?? '';
  if (s.isNotEmpty && '=+-@\t\r'.contains(s[0])) s = "'$s";
  if (s.contains(',') ||
      s.contains('"') ||
      s.contains('\n') ||
      s.contains('\r')) {
    s = '"${s.replaceAll('"', '""')}"';
  }
  return s;
}

/// Plain UTF-8 bytes (no BOM), used for JSON.
List<int> utf8Bytes(String s) => utf8.encode(s);

/// UTF-8 bytes with a BOM so Excel shows Hindi names correctly.
List<int> csvBytes(String csv) => [0xEF, 0xBB, 0xBF, ...utf8.encode(csv)];

String _row(List<String?> cells) => cells.map(csvCell).join(',');

String _typeText(LedgerTxn t) {
  if (t.isReversal)
    return t.type == TxnType.udhaari ? 'Reversal (Udhaari)' : 'Reversal (Jama)';
  return t.type == TxnType.udhaari ? 'Udhaari' : 'Jama';
}

/// Method only means something for money actually received.
String _methodText(LedgerTxn t) {
  if (t.type != TxnType.jama || t.isReversal) return '';
  return switch (t.method) {
    PayMethod.cash => 'Cash',
    PayMethod.upi => 'UPI',
    PayMethod.other => 'Other',
  };
}

String buildReportCsv(ReportSummary s, String periodLabel) {
  final b = StringBuffer();
  void line(List<String?> c) => b.write('${_row(c)}\r\n');

  line(['auraX Khaata report']);
  line(['Period', periodLabel]);
  line(['Total Udhaari (Rs)', rupeesPlain(s.udhaariPaise)]);
  line(['Total Jama (Rs)', rupeesPlain(s.jamaPaise)]);
  line(['Net change in outstanding (Rs)', rupeesPlain(s.netPaise)]);
  line([
    'Outstanding at period end (Rs)',
    rupeesPlain(s.closingOutstandingPaise)
  ]);
  line(['Jama via Cash (Rs)', rupeesPlain(s.jamaCashPaise)]);
  line(['Jama via UPI (Rs)', rupeesPlain(s.jamaUpiPaise)]);
  line(['Jama via Other (Rs)', rupeesPlain(s.jamaOtherPaise)]);
  line(['Entries', '${s.entryCount}']);
  line(['Of which reversals', '${s.reversalCount}']);
  line([]);
  line([
    'Customer ID',
    'Customer',
    'Udhaari (Rs)',
    'Jama (Rs)',
    'Closing balance (Rs)'
  ]);
  for (final r in s.rows) {
    line([
      r.code,
      r.name,
      rupeesPlain(r.udhaariPaise),
      rupeesPlain(r.jamaPaise),
      rupeesPlain(r.closingBalancePaise),
    ]);
  }
  return b.toString();
}

String buildStatementCsv(CustomerStatement st, String periodLabel) {
  final b = StringBuffer();
  void line(List<String?> c) => b.write('${_row(c)}\r\n');

  line(['auraX Khaata customer statement']);
  line(['Customer', st.customer.name]);
  line(['Customer ID', st.customer.code]);
  line(['Period', periodLabel]);
  line(['Opening balance (Rs)', rupeesPlain(st.openingPaise)]);
  line([]);
  line([
    'Date',
    'Type',
    'Method',
    'Udhaari (Rs)',
    'Jama (Rs)',
    'Balance (Rs)',
    'Note'
  ]);
  for (final l in st.lines) {
    final t = l.txn;
    line([
      formatDateTime(t.createdAt),
      _typeText(t),
      _methodText(t),
      t.type == TxnType.udhaari ? rupeesPlain(t.amountPaise) : '',
      t.type == TxnType.jama ? rupeesPlain(t.amountPaise) : '',
      rupeesPlain(l.runningBalancePaise),
      t.note,
    ]);
  }
  line([]);
  line(['Closing balance (Rs)', rupeesPlain(st.closingPaise)]);
  return b.toString();
}

// ---------------------------------------------------------------- PDF
//
// The built-in PDF font has no Devanagari glyphs and no rupee sign, so for
// now text is limited to Latin characters and money is written as "Rs".
// Non-Latin characters (e.g. Hindi names) are replaced by "?" in the PDF.
// The CSV keeps full Unicode. A bundled Unicode font will lift this limit.

String pdfSafe(String s) {
  final b = StringBuffer();
  for (final r in s.runes) {
    b.write(r <= 0xFF ? String.fromCharCode(r) : '?');
  }
  return b.toString();
}

String pdfMoney(int paise) => formatPaise(paise).replaceAll('₹', 'Rs ');

pw.Widget _title(String text) => pw.Text(
      pdfSafe(text),
      style: pw.TextStyle(fontSize: 18, fontWeight: pw.FontWeight.bold),
    );

Future<Uint8List> buildReportPdf(ReportSummary s, String periodLabel) async {
  final doc = pw.Document(title: 'auraX Khaata report');
  doc.addPage(
    pw.MultiPage(
      pageFormat: PdfPageFormat.a4,
      margin: const pw.EdgeInsets.all(32),
      build: (ctx) => [
        _title('auraX Khaata - Report'),
        pw.SizedBox(height: 4),
        pw.Text('Period: ${pdfSafe(periodLabel)}'),
        pw.SizedBox(height: 12),
        pw.TableHelper.fromTextArray(
          headers: ['Item', 'Amount'],
          data: [
            ['Total Udhaari', pdfMoney(s.udhaariPaise)],
            ['Total Jama', pdfMoney(s.jamaPaise)],
            ['Net change in outstanding', pdfMoney(s.netPaise)],
            ['Outstanding at period end', pdfMoney(s.closingOutstandingPaise)],
            ['Jama via Cash', pdfMoney(s.jamaCashPaise)],
            ['Jama via UPI', pdfMoney(s.jamaUpiPaise)],
            ['Jama via Other', pdfMoney(s.jamaOtherPaise)],
          ],
          cellAlignments: {1: pw.Alignment.centerRight},
        ),
        pw.SizedBox(height: 6),
        pw.Text('Entries: ${s.entryCount} (reversals: ${s.reversalCount})'),
        pw.SizedBox(height: 14),
        pw.Text('Customers',
            style: pw.TextStyle(fontSize: 14, fontWeight: pw.FontWeight.bold)),
        pw.SizedBox(height: 4),
        pw.TableHelper.fromTextArray(
          headers: ['ID', 'Customer', 'Udhaari', 'Jama', 'Closing'],
          data: [
            for (final r in s.rows)
              [
                r.code,
                pdfSafe(r.name),
                pdfMoney(r.udhaariPaise),
                pdfMoney(r.jamaPaise),
                pdfMoney(r.closingBalancePaise),
              ],
          ],
          cellAlignments: {
            2: pw.Alignment.centerRight,
            3: pw.Alignment.centerRight,
            4: pw.Alignment.centerRight,
          },
        ),
      ],
    ),
  );
  return doc.save();
}

Future<Uint8List> buildStatementPdf(
    CustomerStatement st, String periodLabel) async {
  final doc = pw.Document(title: 'auraX Khaata statement');
  doc.addPage(
    pw.MultiPage(
      pageFormat: PdfPageFormat.a4,
      margin: const pw.EdgeInsets.all(32),
      build: (ctx) => [
        _title('auraX Khaata - Statement'),
        pw.SizedBox(height: 4),
        pw.Text('Customer: ${pdfSafe(st.customer.name)} (${st.customer.code})'),
        pw.Text('Period: ${pdfSafe(periodLabel)}'),
        pw.Text('Opening balance: ${pdfMoney(st.openingPaise)}'),
        pw.SizedBox(height: 10),
        pw.TableHelper.fromTextArray(
          headers: ['Date', 'Type', 'Method', 'Udhaari', 'Jama', 'Balance'],
          data: [
            for (final l in st.lines)
              [
                formatDateTime(l.txn.createdAt),
                _typeText(l.txn),
                _methodText(l.txn),
                l.txn.type == TxnType.udhaari
                    ? pdfMoney(l.txn.amountPaise)
                    : '',
                l.txn.type == TxnType.jama ? pdfMoney(l.txn.amountPaise) : '',
                pdfMoney(l.runningBalancePaise),
              ],
          ],
          cellAlignments: {
            3: pw.Alignment.centerRight,
            4: pw.Alignment.centerRight,
            5: pw.Alignment.centerRight,
          },
        ),
        pw.SizedBox(height: 8),
        pw.Text('Closing balance: ${pdfMoney(st.closingPaise)}',
            style: pw.TextStyle(fontWeight: pw.FontWeight.bold)),
      ],
    ),
  );
  return doc.save();
}

/// Every ledger entry (including reversals and entries of customers that are
/// in Trash) for a portable spreadsheet copy of the whole ledger.
String buildLedgerCsv(List<Map<String, Object?>> rows) {
  final b = StringBuffer();
  void line(List<String?> c) => b.write('${_row(c)}\r\n');
  line([
    'Customer ID',
    'Customer',
    'Date',
    'Type',
    'Method',
    'Amount (Rs)',
    'Note',
    'Reversal of entry',
    'Customer in Trash',
  ]);
  for (final r in rows) {
    final isRev = r['reverses_id'] != null;
    final type = r['type'] as String;
    final method = r['method'] as String;
    final typeText = isRev
        ? (type == 'udhaari' ? 'Reversal (Udhaari)' : 'Reversal (Jama)')
        : (type == 'udhaari' ? 'Udhaari' : 'Jama');
    final methodText = (type == 'jama' && !isRev)
        ? (method == 'cash'
            ? 'Cash'
            : method == 'upi'
                ? 'UPI'
                : 'Other')
        : '';
    line([
      'C${(r['seq'] as int).toString().padLeft(4, '0')}',
      r['customer_name'] as String,
      formatDateTime(r['created_at'] as int),
      typeText,
      methodText,
      rupeesPlain(r['amount_paise'] as int),
      r['note'] as String?,
      r['reverses_id'] as String?,
      r['customer_deleted_at'] == null ? 'No' : 'Yes',
    ]);
  }
  return b.toString();
}
