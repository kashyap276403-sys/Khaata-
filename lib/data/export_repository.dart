import 'dart:convert';

import 'package:sqflite/sqflite.dart';

import 'app_database.dart';
import 'audit.dart';
import 'change_bus.dart';

/// Full-data export (portable JSON / CSV rows) and "erase everything".
class ExportRepository {
  ExportRepository(this._db, this._bus);

  final Database _db;
  final ChangeBus _bus;

  /// Everything stored on this phone, as JSON. Amounts stay integer paise.
  Future<String> exportJson({DateTime? now}) async {
    final n = (now ?? DateTime.now()).toUtc();
    final data = <String, Object?>{
      'format': 'auraX Khaata full export',
      'format_version': 1,
      'schema_version': AppDatabase.schemaVersion,
      'exported_at_utc': n.toIso8601String(),
      'amounts_unit': 'paise (integer)',
      'customers': await _db.query('customers', orderBy: 'seq'),
      'transactions':
          await _db.query('transactions', orderBy: 'created_at, rowid'),
      'audit_log': await _db.query('audit_log', orderBy: 'at, rowid'),
      'tombstones': await _db.query('tombstones'),
    };
    return const JsonEncoder.withIndent('  ').convert(data);
  }

  /// Every ledger row with its customer, oldest first (for the CSV export).
  Future<List<Map<String, Object?>>> ledgerRows() {
    return _db.rawQuery('''
SELECT t.id AS id, t.type AS type, t.method AS method,
       t.amount_paise AS amount_paise, t.note AS note,
       t.created_at AS created_at, t.reverses_id AS reverses_id,
       c.name AS customer_name, c.seq AS seq, c.deleted_at AS customer_deleted_at
FROM transactions t JOIN customers c ON c.id = t.customer_id
ORDER BY t.created_at, t.rowid''');
  }

  /// Permanently removes ALL customers, entries and history from this phone.
  /// Tombstones for every removed record are kept (ids only, no personal
  /// data) so an older backup can never silently bring them back.
  /// App look-and-feel settings are not touched.
  Future<({int customers, int transactions})> eraseAll({DateTime? now}) async {
    final ts = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final result = await _db.transaction((txn) async {
      final customers = await txn.query('customers', columns: ['id']);
      final txns = await txn.query('transactions', columns: ['id']);
      for (final c in customers) {
        await txn.insert(
            'tombstones',
            {
              'entity': 'customer',
              'entity_id': c['id'],
              'purged_at': ts,
            },
            conflictAlgorithm: ConflictAlgorithm.ignore);
      }
      for (final t in txns) {
        await txn.insert(
            'tombstones',
            {
              'entity': 'transaction',
              'entity_id': t['id'],
              'purged_at': ts,
            },
            conflictAlgorithm: ConflictAlgorithm.ignore);
      }
      await txn.delete('reminder_state');
      // Reversal rows point at their originals, so they go first.
      await txn.delete('transactions', where: 'reverses_id IS NOT NULL');
      await txn.delete('transactions');
      await txn.delete('customers');
      await txn.delete('audit_log');
      await writeAudit(txn,
          entity: 'system',
          entityId: 'all',
          action: 'erase',
          details: {
            'customers': customers.length,
            'transactions': txns.length,
          },
          at: ts);
      return (customers: customers.length, transactions: txns.length);
    });
    _bus.notifyChanged();
    return result;
  }
}
