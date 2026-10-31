import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/data/app_database.dart';
import 'package:khaata/data/change_bus.dart';
import 'package:khaata/data/customer_repository.dart';
import 'package:khaata/data/export_repository.dart';
import 'package:khaata/data/ledger_repository.dart';
import 'package:khaata/data/models.dart';
import 'package:khaata/data/reminder_repository.dart';
import 'package:khaata/reports/exporter.dart';
import 'package:khaata/settings/app_settings.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  late Database db;
  late CustomerRepository customers;
  late LedgerRepository ledger;
  late ExportRepository exports;
  late ReminderRepository reminders;

  setUp(() async {
    db = await AppDatabase.open(
      path: inMemoryDatabasePath,
      factory: databaseFactoryFfi,
    );
    final bus = ChangeBus();
    customers = CustomerRepository(db, bus);
    ledger = LedgerRepository(db, bus);
    exports = ExportRepository(db, bus);
    reminders = ReminderRepository(db, bus);
  });

  tearDown(() async {
    await db.close();
  });

  Future<void> seed() async {
    final a = await customers.add(name: 'Asha', phone: '9876543210');
    final b = await customers.add(name: '=cmd|calc', dueDate: 1);
    final t = await ledger.addTransaction(
        customerId: a.id,
        type: TxnType.udhaari,
        amountPaise: 50050,
        note: 'saman, "chawal"');
    await ledger.reverse(t.id);
    await ledger.addTransaction(
        customerId: a.id,
        type: TxnType.jama,
        amountPaise: 1000,
        method: PayMethod.upi);
    await ledger.addTransaction(
        customerId: b.id, type: TxnType.udhaari, amountPaise: 200);
    await reminders.snooze(b.id, days: 2);
    await customers.softDelete(b.id);
  }

  group('export', () {
    test('JSON holds every record, amounts stay integer paise', () async {
      await seed();
      final map = jsonDecode(await exports.exportJson(now: DateTime.utc(2026, 10, 31)))
          as Map<String, dynamic>;
      expect(map['format_version'], 1);
      expect(map['schema_version'], AppDatabase.schemaVersion);
      expect(map['exported_at_utc'], startsWith('2026-10-31'));
      expect((map['customers'] as List).length, 2); // trashed one included
      final txns = map['transactions'] as List;
      expect(txns.length, 4);
      expect(txns.map((t) => t['amount_paise']), everyElement(isA<int>()));
      expect(txns.first['amount_paise'], 50050);
      expect((map['audit_log'] as List).isNotEmpty, isTrue);
    });

    test('ledger CSV lists all rows, neutralises formulas, no method on udhaari',
        () async {
      await seed();
      final csv = buildLedgerCsv(await exports.ledgerRows());
      final lines = csv.trim().split('\r\n');
      expect(lines.length, 5); // header + 4 entries
      expect(csv, contains("'=cmd|calc"));
      expect(csv, contains('"saman, ""chawal"""'));
      expect(csv, contains('Reversal (Jama)'));
      expect(csv, contains(',500.50,'));
      final udhaari = lines.firstWhere((l) => l.contains(',Udhaari,'));
      expect(udhaari.contains('UPI') || udhaari.contains('Cash'), isFalse);
      final trash = lines.firstWhere((l) => l.contains("'=cmd|calc"));
      expect(trash.endsWith(',Yes'), isTrue);
    });
  });

  group('erase all', () {
    test('removes everything, keeps tombstones, app keeps working', () async {
      await seed();
      final r = await exports.eraseAll();
      expect(r.customers, 2);
      expect(r.transactions, 4);

      expect(await db.query('customers'), isEmpty);
      expect(await db.query('transactions'), isEmpty);
      expect(await db.query('reminder_state'), isEmpty);
      final tomb = await db.query('tombstones');
      expect(tomb.where((t) => t['entity'] == 'customer').length, 2);
      expect(tomb.where((t) => t['entity'] == 'transaction').length, 4);

      // Only the erase marker remains in the activity log, with no names.
      final audit = await db.query('audit_log');
      expect(audit.length, 1);
      expect(audit.single['action'], 'erase');
      expect((audit.single['details'] as String).contains('Asha'), isFalse);

      // The app is usable afterwards.
      final c = await customers.add(name: 'Naya');
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 100);
      expect((await ledger.dashboard()).outstandingPaise, 100);
      expect(await ledger.integrityIssues(), isEmpty);
    });

    test('erasing an empty database is harmless', () async {
      final r = await exports.eraseAll();
      expect(r.customers, 0);
      expect(r.transactions, 0);
    });
  });

  group('integrity check', () {
    test('detects a reversal that does not match its original', () async {
      final c = await customers.add(name: 'Asha');
      final t = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 1000);
      expect(await ledger.integrityIssues(), isEmpty);
      // Simulate corruption: a bad "reversal" with a different amount.
      await db.insert('transactions', {
        'id': 'bad1',
        'customer_id': c.id,
        'type': 'jama',
        'amount_paise': 999,
        'method': 'other',
        'created_at': 5,
        'reverses_id': t.id,
      });
      final issues = await ledger.integrityIssues();
      expect(issues.length, 1);
      expect(issues.single, contains('bad1'));
    });
  });

  group('settings: glass and simple/pro', () {
    test('defaults, change and persistence', () async {
      SharedPreferences.setMockInitialValues({});
      final s = await AppSettings.load();
      expect(s.proMode, isTrue);
      expect(s.glassIndex, 1);
      expect(s.glassAlpha, 0.35);

      await s.setProMode(false);
      await s.setGlassIndex(3);
      final again = await AppSettings.load();
      expect(again.proMode, isFalse);
      expect(again.glassAlpha, 0.75);
    });

    test('bad saved glass value is clamped', () async {
      SharedPreferences.setMockInitialValues({'glass_index': 42});
      final s = await AppSettings.load();
      expect(s.glassIndex, AppSettings.glassAlphas.length - 1);
      expect(s.themeMode, ThemeMode.system);
    });
  });
}
