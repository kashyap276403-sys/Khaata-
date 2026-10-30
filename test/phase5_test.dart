import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/data/app_database.dart';
import 'package:khaata/data/change_bus.dart';
import 'package:khaata/data/customer_repository.dart';
import 'package:khaata/data/ledger_repository.dart';
import 'package:khaata/data/models.dart';
import 'package:khaata/data/reminder_repository.dart';
import 'package:khaata/settings/app_settings.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  late Database db;
  late CustomerRepository customers;
  late LedgerRepository ledger;
  late ReminderRepository reminders;

  final now = DateTime(2026, 10, 30, 12);
  int day(int d) => DateTime(2026, 10, d).millisecondsSinceEpoch;

  setUp(() async {
    db = await AppDatabase.open(
      path: inMemoryDatabasePath,
      factory: databaseFactoryFfi,
    );
    final bus = ChangeBus();
    customers = CustomerRepository(db, bus);
    ledger = LedgerRepository(db, bus);
    reminders = ReminderRepository(db, bus);
  });

  tearDown(() async {
    await db.close();
  });

  Future<Customer> owing(String name, {int? due, int paise = 10000}) async {
    final c = await customers.add(name: name, dueDate: due);
    await ledger.addTransaction(
        customerId: c.id, type: TxnType.udhaari, amountPaise: paise);
    return c;
  }

  group('archive', () {
    test('archived customers move lists and can be restored', () async {
      final c = await owing('Asha');
      await customers.setArchived(c.id, true);
      expect(await customers.list(), isEmpty);
      expect((await customers.list(archived: true)).single.customer.id, c.id);

      await customers.setArchived(c.id, false);
      expect((await customers.list()).length, 1);
      expect(await customers.list(archived: true), isEmpty);
    });

    test('archived customers keep their balance and stay in totals', () async {
      final c = await owing('Asha', paise: 25000);
      await customers.setArchived(c.id, true);
      expect(await customers.balanceFor(c.id), 25000);
      expect((await ledger.dashboard()).outstandingPaise, 25000);
      // history is untouched and entries can still be added
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.jama, amountPaise: 5000);
      expect(await customers.balanceFor(c.id), 20000);
      expect(await customers.recentCustomers(), isEmpty);
    });

    test('archive and unarchive are written to the activity log', () async {
      final c = await customers.add(name: 'Asha');
      await customers.setArchived(c.id, true);
      await customers.setArchived(c.id, false);
      final rows = await db.query('audit_log',
          where: "entity = 'customer'", orderBy: 'at, rowid');
      expect(rows.map((r) => r['action']),
          ['create', 'archive', 'unarchive']);
    });
  });

  group('reminders', () {
    test('lists due-today and overdue customers who still owe', () async {
      await owing('Late', due: day(27));
      await owing('Today', due: day(30));
      await owing('Future', due: day(31));
      await owing('NoDate');
      final list = await reminders.due(now: now);
      expect(list.map((r) => r.customer.name), ['Late', 'Today']);
      expect(list.first.daysOverdue, 3);
      expect(list.last.daysOverdue, 0);
      expect(list.first.balancePaise, 10000);
    });

    test('settled, archived and deleted customers are not listed', () async {
      final settled = await owing('Settled', due: day(20));
      await ledger.addTransaction(
          customerId: settled.id, type: TxnType.jama, amountPaise: 10000);
      final arch = await owing('Arch', due: day(20));
      await customers.setArchived(arch.id, true);
      final gone = await owing('Gone', due: day(20));
      await customers.softDelete(gone.id);
      expect(await reminders.due(now: now), isEmpty);
    });

    test('snooze hides until the time passes', () async {
      final c = await owing('Late', due: day(27));
      await reminders.snooze(c.id, days: 3, now: now);
      expect(await reminders.due(now: now), isEmpty);
      expect(await reminders.due(now: now.add(const Duration(days: 2))),
          isEmpty);
      expect(
          (await reminders.due(now: now.add(const Duration(days: 3, minutes: 1))))
              .length,
          1);
    });

    test('dismiss hides it until the due date changes', () async {
      final c = await owing('Late', due: day(27));
      await reminders.dismiss(c.id);
      expect(await reminders.due(now: now), isEmpty);
      await customers.update(c.id, name: 'Late', dueDate: day(28));
      expect((await reminders.due(now: now)).length, 1);
    });

    test('snooze then dismiss keeps both states working', () async {
      final c = await owing('Late', due: day(27));
      await reminders.snooze(c.id, days: 1, now: now);
      await reminders.dismiss(c.id);
      final far = now.add(const Duration(days: 5));
      expect(await reminders.due(now: far), isEmpty);
    });

    test('permanent delete works for a customer with reminder state', () async {
      final c = await owing('Late', due: day(27));
      await reminders.snooze(c.id, days: 1, now: now);
      await customers.softDelete(c.id);
      await customers.purge(c.id);
      expect(await db.query('reminder_state'), isEmpty);
      expect(await db.query('customers'), isEmpty);
    });
  });

  group('migration to v3', () {
    test('v1 database upgrades with data kept and reminders usable', () async {
      final dir = Directory.systemTemp.createTempSync('khaata_mig3');
      final path = '${dir.path}/old.db';
      try {
        final v1 = await databaseFactoryFfi.openDatabase(
          path,
          options: OpenDatabaseOptions(
            version: 1,
            onCreate: (d, _) => AppDatabase.createV1(d),
          ),
        );
        await v1.insert('customers', {
          'id': 'c1',
          'seq': 1,
          'name': 'Purana',
          'name_normalized': 'purana',
          'name_skeleton': 'prn',
          'due_date': day(20),
          'archived': 0,
          'created_at': 1,
          'updated_at': 1,
        });
        await v1.insert('transactions', {
          'id': 't1',
          'customer_id': 'c1',
          'type': 'udhaari',
          'amount_paise': 9900,
          'method': 'other',
          'created_at': 2,
        });
        await v1.close();

        final v3 = await AppDatabase.open(
            path: path, factory: databaseFactoryFfi);
        expect(await v3.getVersion(), 3);
        final repo = ReminderRepository(v3, ChangeBus());
        final due = await repo.due(now: now);
        expect(due.single.customer.name, 'Purana');
        expect(due.single.balancePaise, 9900);
        await v3.close();
      } finally {
        dir.deleteSync(recursive: true);
      }
    });
  });

  group('settings', () {
    test('defaults, changes and persistence', () async {
      SharedPreferences.setMockInitialValues({});
      final s = await AppSettings.load();
      expect(s.themeMode, ThemeMode.system);
      expect(s.accentIndex, 0);
      expect(s.fontScale, 1.0);

      await s.setThemeMode(ThemeMode.dark);
      await s.setAccent(2);
      await s.setFontIndex(3);

      final again = await AppSettings.load();
      expect(again.themeMode, ThemeMode.dark);
      expect(again.accentIndex, 2);
      expect(again.fontScale, 1.3);
    });

    test('out-of-range saved values fall back safely', () async {
      SharedPreferences.setMockInitialValues(
          {'theme_mode': 99, 'accent_index': 99, 'font_index': -5});
      final s = await AppSettings.load();
      expect(s.themeMode, ThemeMode.dark); // clamped to the last mode
      expect(s.accentIndex, AppSettings.accentColors.length - 1);
      expect(s.fontIndex, 0);
    });

    test('in-memory settings notify listeners', () async {
      final s = AppSettings.memory();
      var calls = 0;
      s.addListener(() => calls++);
      await s.setAccent(1);
      await s.setFontIndex(2);
      expect(calls, 2);
      expect(s.accent, AppSettings.accentColors[1]);
    });
  });
}
