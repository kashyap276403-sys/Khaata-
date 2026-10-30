import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';

import '../core/text_utils.dart';
import 'audit.dart';
import 'change_bus.dart';
import 'models.dart';

/// Deleted customers stay in Trash this many days, then are removed for good.
const int trashRetentionDays = 30;

class CustomerRepository {
  CustomerRepository(this._db, this._bus);

  final Database _db;
  final ChangeBus _bus;
  static const Uuid _uuid = Uuid();

  static const String _balanceJoin = '''
LEFT JOIN (
  SELECT customer_id,
         SUM(CASE type WHEN 'udhaari' THEN amount_paise ELSE -amount_paise END) AS bal
  FROM transactions WHERE deleted_at IS NULL GROUP BY customer_id
) b ON b.customer_id = c.id''';

  String _cleanName(String name) => name.trim().replaceAll(RegExp(r'\s+'), ' ');

  String? _cleanText(String? s) {
    final t = s?.trim() ?? '';
    return t.isEmpty ? null : t;
  }

  String? _cleanPhone(String? s) {
    final d = normalizePhone(s ?? '');
    if (d.isEmpty) return null;
    if (d.length < 7 || d.length > 15) {
      throw ArgumentError('Phone number sahi nahi hai');
    }
    return d;
  }

  Future<String?> _nameOf(DatabaseExecutor ex, String id) async {
    final rows = await ex.query('customers',
        columns: ['name'], where: 'id = ?', whereArgs: [id], limit: 1);
    return rows.isEmpty ? null : rows.first['name'] as String;
  }

  /// Possible duplicates for the form to show. NEVER merges automatically;
  /// the user always decides.
  Future<List<Customer>> findPossibleMatches({
    required String name,
    String? phone,
    String? phone2,
    String? excludeId,
  }) async {
    final n = normalizeName(name);
    final skel = nameSkeleton(name);
    final conds = <String>[];
    final args = <Object?>[];
    if (n.isNotEmpty) {
      conds.add('name_normalized = ?');
      args.add(n);
    }
    if (skel.length >= 3) {
      conds.add('name_skeleton = ?');
      args.add(skel);
    }
    for (final raw in [phone, phone2]) {
      final d = normalizePhone(raw ?? '');
      if (d.length >= 7) {
        conds.add('(phone = ? OR phone2 = ?)');
        args
          ..add(d)
          ..add(d);
      }
    }
    if (conds.isEmpty) return [];
    var where = '(${conds.join(' OR ')}) AND deleted_at IS NULL';
    if (excludeId != null) {
      where += ' AND id <> ?';
      args.add(excludeId);
    }
    final rows = await _db.query('customers',
        where: where, whereArgs: args, orderBy: 'name_normalized');
    return rows.map(Customer.fromMap).toList();
  }

  Future<Customer> add({
    required String name,
    String? phone,
    String? phone2,
    String? address,
    String? notes,
    int? dueDate,
    DateTime? now,
  }) async {
    final cleanName = _cleanName(name);
    if (cleanName.isEmpty) throw ArgumentError('Naam zaroori hai');
    final p1 = _cleanPhone(phone);
    final p2 = _cleanPhone(phone2);
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;

    final customer = await _db.transaction<Customer>((txn) async {
      final rows = await txn
          .rawQuery('SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM customers');
      final seq = rows.first['n'] as int;
      final id = _uuid.v4();
      await txn.insert('customers', {
        'id': id,
        'seq': seq,
        'name': cleanName,
        'name_normalized': normalizeName(cleanName),
        'name_skeleton': nameSkeleton(cleanName),
        'phone': p1,
        'phone2': p2,
        'address': _cleanText(address),
        'notes': _cleanText(notes),
        'due_date': dueDate,
        'archived': 0,
        'created_at': ts,
        'updated_at': ts,
      });
      await writeAudit(txn,
          entity: 'customer',
          entityId: id,
          action: 'create',
          details: {'name': cleanName},
          at: ts);
      return Customer(
        id: id,
        seq: seq,
        name: cleanName,
        phone: p1,
        phone2: p2,
        address: _cleanText(address),
        notes: _cleanText(notes),
        dueDate: dueDate,
        createdAt: ts,
        updatedAt: ts,
      );
    });
    _bus.notifyChanged();
    return customer;
  }

  Future<void> update(
    String id, {
    required String name,
    String? phone,
    String? phone2,
    String? address,
    String? notes,
    int? dueDate,
    DateTime? now,
  }) async {
    final cleanName = _cleanName(name);
    if (cleanName.isEmpty) throw ArgumentError('Naam zaroori hai');
    final p1 = _cleanPhone(phone);
    final p2 = _cleanPhone(phone2);
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;

    await _db.transaction((txn) async {
      final old = await txn.query('customers',
          where: 'id = ? AND deleted_at IS NULL', whereArgs: [id], limit: 1);
      if (old.isEmpty) throw LedgerException('Customer nahi mila');
      final before = Customer.fromMap(old.first);

      final changes = <String, Object?>{};
      void diff(String k, Object? a, Object? b) {
        if (a != b) changes[k] = {'before': a, 'after': b};
      }

      diff('name', before.name, cleanName);
      diff('phone', before.phone, p1);
      diff('phone2', before.phone2, p2);
      diff('address', before.address, _cleanText(address));
      diff('notes', before.notes, _cleanText(notes));
      diff('due_date', before.dueDate, dueDate);

      await txn.update(
        'customers',
        {
          'name': cleanName,
          'name_normalized': normalizeName(cleanName),
          'name_skeleton': nameSkeleton(cleanName),
          'phone': p1,
          'phone2': p2,
          'address': _cleanText(address),
          'notes': _cleanText(notes),
          'due_date': dueDate,
          'updated_at': ts,
        },
        where: 'id = ?',
        whereArgs: [id],
      );
      await writeAudit(txn,
          entity: 'customer',
          entityId: id,
          action: 'update',
          details: {'name': cleanName, 'changes': changes},
          at: ts);
    });
    _bus.notifyChanged();
  }

  Future<void> setArchived(String id, bool archived, {DateTime? now}) async {
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;
    await _db.transaction((txn) async {
      final n = await txn.update(
        'customers',
        {'archived': archived ? 1 : 0, 'updated_at': ts},
        where: 'id = ? AND deleted_at IS NULL',
        whereArgs: [id],
      );
      if (n == 0) throw LedgerException('Customer nahi mila');
      await writeAudit(txn,
          entity: 'customer',
          entityId: id,
          action: archived ? 'archive' : 'unarchive',
          details: {'name': await _nameOf(txn, id)},
          at: ts);
    });
    _bus.notifyChanged();
  }

  /// Soft delete: the row and ALL its transactions stay in the database and
  /// can be restored from Trash for [trashRetentionDays] days.
  Future<void> softDelete(String id, {DateTime? now}) async {
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;
    await _db.transaction((txn) async {
      final n = await txn.update(
        'customers',
        {'deleted_at': ts, 'updated_at': ts},
        where: 'id = ? AND deleted_at IS NULL',
        whereArgs: [id],
      );
      if (n == 0) throw LedgerException('Customer nahi mila');
      await writeAudit(txn,
          entity: 'customer',
          entityId: id,
          action: 'delete',
          details: {'name': await _nameOf(txn, id)},
          at: ts);
    });
    _bus.notifyChanged();
  }

  Future<void> restore(String id, {DateTime? now}) async {
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;
    await _db.transaction((txn) async {
      final n = await txn.update(
        'customers',
        {'deleted_at': null, 'updated_at': ts},
        where: 'id = ? AND deleted_at IS NOT NULL',
        whereArgs: [id],
      );
      if (n == 0) throw LedgerException('Yeh customer Trash me nahi hai');
      await writeAudit(txn,
          entity: 'customer',
          entityId: id,
          action: 'restore',
          details: {'name': await _nameOf(txn, id)},
          at: ts);
    });
    _bus.notifyChanged();
  }

  /// Permanently removes a customer that is in Trash, with all its
  /// transactions. Personal details are scrubbed from the audit log and a
  /// tombstone is written, so an old backup can never silently bring the
  /// records back.
  Future<void> purge(String id, {DateTime? now}) async {
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;
    await _db.transaction((txn) async {
      final rows = await txn.query('customers',
          columns: ['id'],
          where: 'id = ? AND deleted_at IS NOT NULL',
          whereArgs: [id],
          limit: 1);
      if (rows.isEmpty) {
        throw LedgerException(
            'Sirf Trash ke customer hamesha ke liye delete ho sakte hain');
      }
      final txnRows = await txn.query('transactions',
          columns: ['id'], where: 'customer_id = ?', whereArgs: [id]);
      final txnIds = txnRows.map((r) => r['id'] as String).toList();

      for (final eid in [id, ...txnIds]) {
        await txn.update('audit_log', {'details': null},
            where: 'entity_id = ?', whereArgs: [eid]);
      }
      // Reversal rows point at their originals, so they go first.
      await txn.delete('transactions',
          where: 'customer_id = ? AND reverses_id IS NOT NULL',
          whereArgs: [id]);
      await txn
          .delete('transactions', where: 'customer_id = ?', whereArgs: [id]);
      await txn.delete('customers', where: 'id = ?', whereArgs: [id]);

      await txn.insert('tombstones',
          {'entity': 'customer', 'entity_id': id, 'purged_at': ts});
      for (final tid in txnIds) {
        await txn.insert('tombstones',
            {'entity': 'transaction', 'entity_id': tid, 'purged_at': ts});
      }
      await writeAudit(txn,
          entity: 'customer', entityId: id, action: 'purge', at: ts);
    });
    _bus.notifyChanged();
  }

  /// Removes Trash items older than [trashRetentionDays]. Returns how many.
  Future<int> purgeExpired({DateTime? now}) async {
    final n = now ?? DateTime.now();
    final cutoff = n
        .subtract(const Duration(days: trashRetentionDays))
        .millisecondsSinceEpoch;
    final rows = await _db.query('customers',
        columns: ['id'],
        where: 'deleted_at IS NOT NULL AND deleted_at < ?',
        whereArgs: [cutoff]);
    for (final r in rows) {
      await purge(r['id'] as String, now: n);
    }
    return rows.length;
  }

  Future<Customer?> getById(String id) async {
    final rows = await _db.query('customers',
        where: 'id = ? AND deleted_at IS NULL', whereArgs: [id], limit: 1);
    return rows.isEmpty ? null : Customer.fromMap(rows.first);
  }

  Future<int> balanceFor(String customerId) async {
    final rows = await _db.rawQuery('''
SELECT COALESCE(SUM(CASE type WHEN 'udhaari' THEN amount_paise ELSE -amount_paise END), 0) AS bal
FROM transactions WHERE customer_id = ? AND deleted_at IS NULL''',
        [customerId]);
    return rows.first['bal'] as int;
  }

  /// Active customers (or archived ones when [archived] is true) with their
  /// balance. [query] matches name, phone or customer ID/code.
  Future<List<CustomerWithBalance>> list({
    String query = '',
    bool archived = false,
  }) async {
    final args = <Object?>[archived ? 1 : 0];
    var where = 'c.deleted_at IS NULL AND c.archived = ?';
    final q = query.trim();
    if (q.isNotEmpty) {
      final like = '%${escapeLike(normalizeName(q))}%';
      final digits = normalizePhone(q);
      final phoneLike = '%${escapeLike(digits.isEmpty ? q : digits)}%';
      where += ''' AND (c.name_normalized LIKE ? ESCAPE '\\'
        OR c.phone LIKE ? ESCAPE '\\' OR c.phone2 LIKE ? ESCAPE '\\'
        OR LOWER('C' || SUBSTR('0000' || c.seq, -4, 4)) LIKE ? ESCAPE '\\'
        OR c.id LIKE ? ESCAPE '\\')''';
      args.addAll([like, phoneLike, phoneLike, like, like]);
    }
    final rows = await _db.rawQuery('''
SELECT c.*, COALESCE(b.bal, 0) AS balance
FROM customers c $_balanceJoin
WHERE $where
ORDER BY c.name_normalized, c.seq''', args);
    return rows
        .map((r) =>
            CustomerWithBalance(Customer.fromMap(r), r['balance'] as int))
        .toList();
  }

  /// Active customers ordered by their most recent entry (for Quick Entry).
  Future<List<CustomerWithBalance>> recentCustomers({int limit = 6}) async {
    final rows = await _db.rawQuery('''
SELECT c.*, COALESCE(b.bal, 0) AS balance, m.last_at AS last_at
FROM customers c
JOIN (
  SELECT customer_id, MAX(created_at) AS last_at
  FROM transactions WHERE deleted_at IS NULL GROUP BY customer_id
) m ON m.customer_id = c.id
$_balanceJoin
WHERE c.deleted_at IS NULL AND c.archived = 0
ORDER BY m.last_at DESC, c.seq DESC
LIMIT ?''', [limit]);
    return rows
        .map((r) =>
            CustomerWithBalance(Customer.fromMap(r), r['balance'] as int))
        .toList();
  }

  /// Customers currently in Trash, most recently deleted first.
  Future<List<CustomerWithBalance>> listDeleted() async {
    final rows = await _db.rawQuery('''
SELECT c.*, COALESCE(b.bal, 0) AS balance
FROM customers c $_balanceJoin
WHERE c.deleted_at IS NOT NULL
ORDER BY c.deleted_at DESC''');
    return rows
        .map((r) =>
            CustomerWithBalance(Customer.fromMap(r), r['balance'] as int))
        .toList();
  }
}
