import 'package:sqflite/sqflite.dart';

import 'models.dart';

/// Per-customer line of a period report.
class CustomerReportRow {
  const CustomerReportRow({
    required this.customerId,
    required this.name,
    required this.code,
    required this.udhaariPaise,
    required this.jamaPaise,
    required this.closingBalancePaise,
  });

  final String customerId;
  final String name;
  final String code;
  final int udhaariPaise;
  final int jamaPaise;

  /// Balance at the end of the period (all history up to the end date).
  final int closingBalancePaise;
}

/// Totals for a period. Every number comes from the same `transactions`
/// table as the balances, with the same rule:
///   net = SUM(udhaari) - SUM(jama)  (reversal entries are ordinary rows)
class ReportSummary {
  const ReportSummary({
    required this.fromMs,
    required this.toMs,
    required this.udhaariPaise,
    required this.jamaPaise,
    required this.entryCount,
    required this.reversalCount,
    required this.jamaCashPaise,
    required this.jamaUpiPaise,
    required this.jamaOtherPaise,
    required this.closingOutstandingPaise,
    required this.rows,
  });

  /// Period is [fromMs, toMs) in epoch millis.
  final int fromMs;
  final int toMs;
  final int udhaariPaise;
  final int jamaPaise;
  final int entryCount;
  final int reversalCount;

  /// Jama split by how the money was received. Reversal entries are left out
  /// of this split because they are corrections, not money received.
  final int jamaCashPaise;
  final int jamaUpiPaise;
  final int jamaOtherPaise;

  /// Total outstanding at the end of the period.
  final int closingOutstandingPaise;
  final List<CustomerReportRow> rows;

  /// Change in outstanding during the period.
  int get netPaise => udhaariPaise - jamaPaise;
}

class StatementLine {
  const StatementLine(this.txn, this.runningBalancePaise);
  final LedgerTxn txn;
  final int runningBalancePaise;
}

class CustomerStatement {
  const CustomerStatement({
    required this.customer,
    required this.fromMs,
    required this.toMs,
    required this.openingPaise,
    required this.lines,
    required this.closingPaise,
  });

  final Customer customer;
  final int fromMs;
  final int toMs;
  final int openingPaise;
  final List<StatementLine> lines;
  final int closingPaise;
}

class ReportRepository {
  ReportRepository(this._db);

  final Database _db;

  static const String _signed =
      "CASE t.type WHEN 'udhaari' THEN t.amount_paise ELSE -t.amount_paise END";

  Future<ReportSummary> summary({
    required int fromMs,
    required int toMs,
  }) async {
    final groups = await _db.rawQuery('''
SELECT t.type AS type, t.method AS method,
       (t.reverses_id IS NOT NULL) AS rev,
       SUM(t.amount_paise) AS total, COUNT(*) AS n
FROM transactions t JOIN customers c ON c.id = t.customer_id
WHERE t.deleted_at IS NULL AND c.deleted_at IS NULL
  AND t.created_at >= ? AND t.created_at < ?
GROUP BY t.type, t.method, rev''', [fromMs, toMs]);

    var udhaari = 0, jama = 0, count = 0, reversals = 0;
    var cash = 0, upi = 0, other = 0;
    for (final g in groups) {
      final total = g['total'] as int;
      final n = g['n'] as int;
      final isRev = (g['rev'] as int) == 1;
      count += n;
      if (isRev) reversals += n;
      if (g['type'] == 'udhaari') {
        udhaari += total;
      } else {
        jama += total;
        if (!isRev) {
          switch (g['method']) {
            case 'cash':
              cash += total;
            case 'upi':
              upi += total;
            default:
              other += total;
          }
        }
      }
    }

    final outstanding = await _db.rawQuery('''
SELECT COALESCE(SUM($_signed), 0) AS bal
FROM transactions t JOIN customers c ON c.id = t.customer_id
WHERE t.deleted_at IS NULL AND c.deleted_at IS NULL AND t.created_at < ?''',
        [toMs]);

    final rowData = await _db.rawQuery('''
SELECT c.id AS id, c.name AS name, c.seq AS seq,
  SUM(CASE WHEN t.created_at >= ? AND t.type = 'udhaari' THEN t.amount_paise ELSE 0 END) AS u,
  SUM(CASE WHEN t.created_at >= ? AND t.type = 'jama' THEN t.amount_paise ELSE 0 END) AS j,
  SUM(CASE WHEN t.created_at >= ? THEN 1 ELSE 0 END) AS in_range,
  SUM($_signed) AS closing
FROM transactions t JOIN customers c ON c.id = t.customer_id
WHERE t.deleted_at IS NULL AND c.deleted_at IS NULL AND t.created_at < ?
GROUP BY c.id
HAVING in_range > 0
ORDER BY c.name_normalized, c.seq''', [fromMs, fromMs, fromMs, toMs]);

    return ReportSummary(
      fromMs: fromMs,
      toMs: toMs,
      udhaariPaise: udhaari,
      jamaPaise: jama,
      entryCount: count,
      reversalCount: reversals,
      jamaCashPaise: cash,
      jamaUpiPaise: upi,
      jamaOtherPaise: other,
      closingOutstandingPaise: outstanding.first['bal'] as int,
      rows: rowData
          .map((r) => CustomerReportRow(
                customerId: r['id'] as String,
                name: r['name'] as String,
                code: 'C${(r['seq'] as int).toString().padLeft(4, '0')}',
                udhaariPaise: r['u'] as int,
                jamaPaise: r['j'] as int,
                closingBalancePaise: r['closing'] as int,
              ))
          .toList(),
    );
  }

  /// Opening balance, every entry in [fromMs, toMs) with a running balance,
  /// and the closing balance for one customer.
  Future<CustomerStatement?> statement({
    required String customerId,
    required int fromMs,
    required int toMs,
  }) async {
    final c = await _db.query('customers',
        where: 'id = ? AND deleted_at IS NULL',
        whereArgs: [customerId],
        limit: 1);
    if (c.isEmpty) return null;

    final opening = await _db.rawQuery('''
SELECT COALESCE(SUM($_signed), 0) AS bal FROM transactions t
WHERE t.customer_id = ? AND t.deleted_at IS NULL AND t.created_at < ?''',
        [customerId, fromMs]);
    var running = opening.first['bal'] as int;
    final openingPaise = running;

    final rows = await _db.query('transactions',
        where: 'customer_id = ? AND deleted_at IS NULL '
            'AND created_at >= ? AND created_at < ?',
        whereArgs: [customerId, fromMs, toMs],
        orderBy: 'created_at ASC, rowid ASC');
    final lines = <StatementLine>[];
    for (final r in rows) {
      final t = LedgerTxn.fromMap(r);
      running += t.type == TxnType.udhaari ? t.amountPaise : -t.amountPaise;
      lines.add(StatementLine(t, running));
    }
    return CustomerStatement(
      customer: Customer.fromMap(c.first),
      fromMs: fromMs,
      toMs: toMs,
      openingPaise: openingPaise,
      lines: lines,
      closingPaise: running,
    );
  }
}
