import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';

import '../core/money.dart';
import '../core/text_utils.dart';
import 'activity.dart';
import 'audit.dart';
import 'change_bus.dart';
import 'models.dart';

class LedgerRepository {
  LedgerRepository(this._db, this._bus);

  final Database _db;
  final ChangeBus _bus;
  static const Uuid _uuid = Uuid();

  static const String _signedAmount =
      "CASE t.type WHEN 'udhaari' THEN t.amount_paise ELSE -t.amount_paise END";

  /// Adds one ledger entry atomically.
  ///
  /// If [idempotencyKey] was already used, the ORIGINAL entry is returned and
  /// nothing new is written (protects against double taps / retries).
  Future<LedgerTxn> addTransaction({
    required String customerId,
    required TxnType type,
    required int amountPaise,
    PayMethod method = PayMethod.cash,
    String? note,
    String? idempotencyKey,
    DateTime? now,
  }) async {
    if (amountPaise < 1) {
      throw LedgerException('Amount zero se zyada hona chahiye');
    }
    if (amountPaise > maxAmountPaise) {
      throw LedgerException('Amount bahut bada hai');
    }
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final cleanNote = (note?.trim().isEmpty ?? true) ? null : note!.trim();
    // Udhaari moves no money, so it never carries Cash/UPI.
    if (type == TxnType.udhaari) method = PayMethod.other;

    final result = await _db.transaction<(LedgerTxn, bool)>((txn) async {
      if (idempotencyKey != null) {
        final existing = await txn.query('transactions',
            where: 'idempotency_key = ?',
            whereArgs: [idempotencyKey],
            limit: 1);
        if (existing.isNotEmpty) {
          return (LedgerTxn.fromMap(existing.first), false);
        }
      }
      final c = await txn.query('customers',
          columns: ['id', 'name'],
          where: 'id = ? AND deleted_at IS NULL',
          whereArgs: [customerId],
          limit: 1);
      if (c.isEmpty) throw LedgerException('Customer nahi mila');

      final t = LedgerTxn(
        id: _uuid.v4(),
        customerId: customerId,
        type: type,
        amountPaise: amountPaise,
        method: method,
        note: cleanNote,
        createdAt: ts,
        idempotencyKey: idempotencyKey,
      );
      await txn.insert('transactions', _toRow(t));
      await writeAudit(txn,
          entity: 'transaction',
          entityId: t.id,
          action: 'create',
          details: {
            'customer_id': customerId,
            'customer_name': c.first['name'],
            'type': type.name,
            'amount_paise': amountPaise,
            'method': method.name,
          },
          at: ts);
      return (t, true);
    });
    if (result.$2) _bus.notifyChanged();
    return result.$1;
  }

  /// Corrects history WITHOUT editing or deleting it: writes a new entry of
  /// the opposite type for the same amount, linked to the original.
  Future<LedgerTxn> reverse(
    String txnId, {
    String? idempotencyKey,
    String? note,
    DateTime? now,
  }) async {
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;

    final result = await _db.transaction<(LedgerTxn, bool)>((txn) async {
      if (idempotencyKey != null) {
        final existing = await txn.query('transactions',
            where: 'idempotency_key = ?',
            whereArgs: [idempotencyKey],
            limit: 1);
        if (existing.isNotEmpty) {
          return (LedgerTxn.fromMap(existing.first), false);
        }
      }
      final rows = await txn.query('transactions',
          where: 'id = ? AND deleted_at IS NULL', whereArgs: [txnId], limit: 1);
      if (rows.isEmpty) throw LedgerException('Entry nahi mili');
      final orig = LedgerTxn.fromMap(rows.first);
      if (orig.isReversal) {
        throw LedgerException('Reversal ko dobara reverse nahi kar sakte');
      }
      final already = await txn.query('transactions',
          columns: ['id'],
          where: 'reverses_id = ?',
          whereArgs: [txnId],
          limit: 1);
      if (already.isNotEmpty) {
        throw LedgerException('Yeh entry pehle hi reverse ho chuki hai');
      }
      final cust = await txn.query('customers',
          columns: ['name'],
          where: 'id = ?',
          whereArgs: [orig.customerId],
          limit: 1);

      final t = LedgerTxn(
        id: _uuid.v4(),
        customerId: orig.customerId,
        type: orig.type.opposite,
        amountPaise: orig.amountPaise,
        method: orig.method,
        note: (note?.trim().isEmpty ?? true) ? 'Reversal' : note!.trim(),
        createdAt: ts,
        idempotencyKey: idempotencyKey,
        reversesId: orig.id,
      );
      await txn.insert('transactions', _toRow(t));
      await writeAudit(txn,
          entity: 'transaction',
          entityId: t.id,
          action: 'reverse',
          details: {
            'reverses_id': orig.id,
            'customer_name': cust.isEmpty ? null : cust.first['name'],
            'type': t.type.name,
            'amount_paise': t.amountPaise,
          },
          at: ts);
      return (t, true);
    });
    if (result.$2) _bus.notifyChanged();
    return result.$1;
  }

  Map<String, Object?> _toRow(LedgerTxn t) => {
        'id': t.id,
        'customer_id': t.customerId,
        'type': t.type.name,
        'amount_paise': t.amountPaise,
        'method': t.method.name,
        'note': t.note,
        'created_at': t.createdAt,
        'idempotency_key': t.idempotencyKey,
        'reverses_id': t.reversesId,
      };

  Future<List<LedgerTxn>> transactionsFor(String customerId) async {
    final rows = await _db.query('transactions',
        where: 'customer_id = ? AND deleted_at IS NULL',
        whereArgs: [customerId],
        orderBy: 'created_at DESC, rowid DESC');
    return rows.map(LedgerTxn.fromMap).toList();
  }

  Future<List<RecentTxn>> recent({int limit = 5}) =>
      history(limit: limit, newestFirst: true);

  /// The latest ordinary entry (reversals are skipped), for "repeat last".
  Future<RecentTxn?> lastEntry() async {
    final rows = await _db.rawQuery('''
SELECT t.*, c.name AS customer_name
FROM transactions t JOIN customers c ON c.id = t.customer_id
WHERE t.deleted_at IS NULL AND c.deleted_at IS NULL
  AND c.archived = 0 AND t.reverses_id IS NULL
ORDER BY t.created_at DESC, t.rowid DESC
LIMIT 1''');
    if (rows.isEmpty) return null;
    final r = rows.first;
    return RecentTxn(LedgerTxn.fromMap(r), r['customer_name'] as String);
  }

  /// Global history with optional date window [fromMs, toMs) in epoch
  /// millis, customer-name search and sort order.
  Future<List<RecentTxn>> history({
    int? fromMs,
    int? toMs,
    String query = '',
    bool newestFirst = true,
    int limit = 500,
  }) async {
    final where = <String>['t.deleted_at IS NULL', 'c.deleted_at IS NULL'];
    final args = <Object?>[];
    if (fromMs != null) {
      where.add('t.created_at >= ?');
      args.add(fromMs);
    }
    if (toMs != null) {
      where.add('t.created_at < ?');
      args.add(toMs);
    }
    final q = normalizeName(query);
    if (q.isNotEmpty) {
      where.add("c.name_normalized LIKE ? ESCAPE '\\'");
      args.add('%${escapeLike(q)}%');
    }
    final dir = newestFirst ? 'DESC' : 'ASC';
    final rows = await _db.rawQuery('''
SELECT t.*, c.name AS customer_name
FROM transactions t JOIN customers c ON c.id = t.customer_id
WHERE ${where.join(' AND ')}
ORDER BY t.created_at $dir, t.rowid $dir
LIMIT ?''', [...args, limit]);
    return rows
        .map((r) =>
            RecentTxn(LedgerTxn.fromMap(r), r['customer_name'] as String))
        .toList();
  }

  /// Audit trail, newest first.
  Future<List<ActivityItem>> activity({int limit = 200}) async {
    final rows = await _db.query('audit_log',
        orderBy: 'at DESC, rowid DESC', limit: limit);
    return rows.map(ActivityItem.fromMap).toList();
  }

  Future<DashboardSummary> dashboard({DateTime? now}) async {
    final n = now ?? DateTime.now();
    final start = DateTime(n.year, n.month, n.day).millisecondsSinceEpoch;
    final end = DateTime(n.year, n.month, n.day + 1).millisecondsSinceEpoch;

    final totals = await _db.rawQuery('''
SELECT
  COALESCE(SUM($_signedAmount), 0) AS outstanding,
  COALESCE(SUM(CASE WHEN t.type = 'udhaari' AND t.created_at >= ? AND t.created_at < ?
                    THEN t.amount_paise ELSE 0 END), 0) AS today_udhaari,
  COALESCE(SUM(CASE WHEN t.type = 'jama' AND t.created_at >= ? AND t.created_at < ?
                    THEN t.amount_paise ELSE 0 END), 0) AS today_jama
FROM transactions t JOIN customers c ON c.id = t.customer_id
WHERE t.deleted_at IS NULL AND c.deleted_at IS NULL''',
        [start, end, start, end]);

    final count = await _db.rawQuery(
        'SELECT COUNT(*) AS n FROM customers WHERE deleted_at IS NULL');

    final pending = await _db.rawQuery('''
SELECT COUNT(*) AS n FROM (
  SELECT t.customer_id, SUM($_signedAmount) AS bal
  FROM transactions t JOIN customers c ON c.id = t.customer_id
  WHERE t.deleted_at IS NULL AND c.deleted_at IS NULL
  GROUP BY t.customer_id HAVING bal > 0
)''');

    return DashboardSummary(
      outstandingPaise: totals.first['outstanding'] as int,
      todayUdhaariPaise: totals.first['today_udhaari'] as int,
      todayJamaPaise: totals.first['today_jama'] as int,
      customerCount: count.first['n'] as int,
      pendingCount: pending.first['n'] as int,
    );
  }

  /// Returns human-readable problems; an empty list means the ledger is
  /// internally consistent.
  Future<List<String>> integrityIssues() async {
    final issues = <String>[];

    final orphans = await _db.rawQuery('''
SELECT t.id FROM transactions t
LEFT JOIN customers c ON c.id = t.customer_id WHERE c.id IS NULL''');
    for (final r in orphans) {
      issues.add('Transaction ${r['id']} ka customer maujood nahi');
    }

    final badAmounts = await _db
        .rawQuery('SELECT id FROM transactions WHERE amount_paise <= 0');
    for (final r in badAmounts) {
      issues.add('Transaction ${r['id']} ka amount sahi nahi');
    }

    final badReversals = await _db.rawQuery('''
SELECT r.id FROM transactions r JOIN transactions o ON o.id = r.reverses_id
WHERE r.customer_id <> o.customer_id
   OR r.amount_paise <> o.amount_paise
   OR r.type = o.type''');
    for (final r in badReversals) {
      issues.add('Reversal ${r['id']} original se match nahi karta');
    }
    return issues;
  }
}
