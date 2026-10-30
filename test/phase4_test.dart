import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/core/money.dart';
import 'package:khaata/data/activity.dart';
import 'package:khaata/data/app_database.dart';
import 'package:khaata/data/change_bus.dart';
import 'package:khaata/data/customer_repository.dart';
import 'package:khaata/data/ledger_repository.dart';
import 'package:khaata/data/models.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  late Database db;
  late CustomerRepository customers;
  late LedgerRepository ledger;

  setUp(() async {
    db = await AppDatabase.open(
      path: inMemoryDatabasePath,
      factory: databaseFactoryFfi,
    );
    final bus = ChangeBus();
    customers = CustomerRepository(db, bus);
    ledger = LedgerRepository(db, bus);
  });

  tearDown(() async {
    await db.close();
  });

  test('paiseToInput is the inverse of parseRupeesToPaise', () {
    expect(paiseToInput(5000), '50');
    expect(paiseToInput(5050), '50.50');
    expect(paiseToInput(5), '0.05');
    expect(parseRupeesToPaise(paiseToInput(123456)), 123456);
  });

  group('migration v1 -> v2', () {
    test('keeps existing data and adds tombstones table', () async {
      final dir = Directory.systemTemp.createTempSync('khaata_mig');
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
          'name': 'Purana Customer',
          'name_normalized': 'purana customer',
          'name_skeleton': 'prncstmr',
          'archived': 0,
          'created_at': 1,
          'updated_at': 1,
        });
        await v1.insert('transactions', {
          'id': 't1',
          'customer_id': 'c1',
          'type': 'udhaari',
          'amount_paise': 12345,
          'method': 'cash',
          'created_at': 2,
        });
        await v1.close();

        final v2 = await AppDatabase.open(
          path: path,
          factory: databaseFactoryFfi,
        );
        final c = await v2.query('customers');
        final t = await v2.query('transactions');
        expect(c.single['name'], 'Purana Customer');
        expect(t.single['amount_paise'], 12345);
        // New table exists and is usable.
        await v2.insert('tombstones',
            {'entity': 'customer', 'entity_id': 'x', 'purged_at': 1});
        expect((await v2.query('tombstones')).length, 1);
        expect(await v2.getVersion(), AppDatabase.schemaVersion);
        await v2.close();
      } finally {
        dir.deleteSync(recursive: true);
      }
    });
  });

  group('trash', () {
    test('purge removes customer and all entries, leaves tombstones', () async {
      final c = await customers.add(name: 'Ramesh', phone: '9876543210');
      final a = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 50000);
      final r = await ledger.reverse(a.id);
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.jama, amountPaise: 100);

      // A customer must be in Trash first.
      await expectLater(customers.purge(c.id), throwsA(isA<LedgerException>()));

      await customers.softDelete(c.id);
      await customers.purge(c.id);

      expect(await db.query('customers'), isEmpty);
      expect(await db.query('transactions'), isEmpty);
      final tomb = await db.query('tombstones');
      expect(tomb.where((e) => e['entity'] == 'customer').length, 1);
      expect(tomb.where((e) => e['entity'] == 'transaction').length, 3);
      expect(tomb.map((e) => e['entity_id']), containsAll([c.id, a.id, r.id]));

      // Personal details are scrubbed from the audit trail.
      final leaked = await db.rawQuery(
          "SELECT details FROM audit_log WHERE details LIKE '%Ramesh%'");
      expect(leaked, isEmpty);
      expect(await customers.listDeleted(), isEmpty);
      expect(await ledger.integrityIssues(), isEmpty);
    });

    test('purge cannot be done twice', () async {
      final c = await customers.add(name: 'Ramesh');
      await customers.softDelete(c.id);
      await customers.purge(c.id);
      await expectLater(customers.purge(c.id), throwsA(isA<LedgerException>()));
    });

    test('purgeExpired only removes items older than retention', () async {
      final old = await customers.add(name: 'Purana');
      final fresh = await customers.add(name: 'Naya');
      final now = DateTime(2026, 10, 30);
      await customers.softDelete(old.id,
          now: now.subtract(const Duration(days: trashRetentionDays + 1)));
      await customers.softDelete(fresh.id,
          now: now.subtract(const Duration(days: 5)));

      final removed = await customers.purgeExpired(now: now);
      expect(removed, 1);
      final left = await customers.listDeleted();
      expect(left.map((e) => e.customer.id), [fresh.id]);
    });

    test('restore brings customer and balance back', () async {
      final c = await customers.add(name: 'Ramesh');
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 7000);
      await customers.softDelete(c.id);
      final trash = await customers.listDeleted();
      expect(trash.single.balancePaise, 7000);
      await customers.restore(c.id);
      expect(await customers.balanceFor(c.id), 7000);
      expect(await customers.listDeleted(), isEmpty);
    });
  });

  group('history and activity', () {
    test('date window, search and sort order', () async {
      final a = await customers.add(name: 'Asha');
      final b = await customers.add(name: 'Bunty');
      await ledger.addTransaction(
          customerId: a.id,
          type: TxnType.udhaari,
          amountPaise: 100,
          now: DateTime(2026, 10, 1, 9));
      await ledger.addTransaction(
          customerId: b.id,
          type: TxnType.udhaari,
          amountPaise: 200,
          now: DateTime(2026, 10, 15, 9));
      await ledger.addTransaction(
          customerId: a.id,
          type: TxnType.jama,
          amountPaise: 300,
          now: DateTime(2026, 10, 30, 9));

      final all = await ledger.history();
      expect(all.map((e) => e.txn.amountPaise), [300, 200, 100]);

      final asc = await ledger.history(newestFirst: false);
      expect(asc.map((e) => e.txn.amountPaise), [100, 200, 300]);

      final window = await ledger.history(
        fromMs: DateTime(2026, 10, 10).millisecondsSinceEpoch,
        toMs: DateTime(2026, 10, 20).millisecondsSinceEpoch,
      );
      expect(window.single.txn.amountPaise, 200);

      final byName = await ledger.history(query: 'ash');
      expect(byName.map((e) => e.customerName).toSet(), {'Asha'});
      expect(byName.length, 2);
    });

    test('history hides entries of deleted customers', () async {
      final a = await customers.add(name: 'Asha');
      await ledger.addTransaction(
          customerId: a.id, type: TxnType.udhaari, amountPaise: 100);
      await customers.softDelete(a.id);
      expect(await ledger.history(), isEmpty);
    });

    test('activity lists create, edit, reverse and trash actions', () async {
      final c = await customers.add(name: 'Ramesh');
      await customers.update(c.id, name: 'Ramesh Kumar');
      final t = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 50050);
      await ledger.reverse(t.id);
      await customers.softDelete(c.id);

      final items = await ledger.activity();
      final text = items.map(describeActivity).toList();
      expect(items.length, 5);
      expect(text.first, contains('Trash'));
      expect(text.any((s) => s.contains('Udhaari') && s.contains('500.50')),
          isTrue);
      expect(text.any((s) => s.contains('Galti sudhari')), isTrue);
      final edit = text.firstWhere((s) => s.contains('edit kiya'));
      expect(edit, contains('Ramesh → Ramesh Kumar'));
    });
  });

  group('payment method rule', () {
    test('udhaari never stores cash/upi; jama keeps its method', () async {
      final c = await customers.add(name: 'Asha');
      final u = await ledger.addTransaction(
        customerId: c.id,
        type: TxnType.udhaari,
        amountPaise: 1000,
        method: PayMethod.upi,
      );
      final j = await ledger.addTransaction(
        customerId: c.id,
        type: TxnType.jama,
        amountPaise: 500,
        method: PayMethod.upi,
      );
      expect(u.method, PayMethod.other);
      expect(j.method, PayMethod.upi);
      final rows = await ledger.transactionsFor(c.id);
      expect(rows.firstWhere((t) => t.id == u.id).method, PayMethod.other);
    });
  });

  group('quick entry helpers', () {
    test('recentCustomers orders by latest entry', () async {
      final a = await customers.add(name: 'Asha');
      final b = await customers.add(name: 'Bunty');
      await customers.add(name: 'Chotu'); // no entries: not listed
      await ledger.addTransaction(
          customerId: a.id,
          type: TxnType.udhaari,
          amountPaise: 100,
          now: DateTime(2026, 10, 1));
      await ledger.addTransaction(
          customerId: b.id,
          type: TxnType.udhaari,
          amountPaise: 100,
          now: DateTime(2026, 10, 2));
      final r = await customers.recentCustomers();
      expect(r.map((e) => e.customer.name), ['Bunty', 'Asha']);
    });

    test('lastEntry skips reversals and deleted customers', () async {
      final a = await customers.add(name: 'Asha');
      final b = await customers.add(name: 'Bunty');
      final t1 = await ledger.addTransaction(
          customerId: a.id,
          type: TxnType.udhaari,
          amountPaise: 100,
          now: DateTime(2026, 10, 1));
      final t2 = await ledger.addTransaction(
          customerId: b.id,
          type: TxnType.udhaari,
          amountPaise: 200,
          now: DateTime(2026, 10, 2));
      await ledger.reverse(t2.id, now: DateTime(2026, 10, 3));
      expect((await ledger.lastEntry())!.txn.id, t2.id);

      await customers.softDelete(b.id);
      expect((await ledger.lastEntry())!.txn.id, t1.id);

      await customers.softDelete(a.id);
      expect(await ledger.lastEntry(), isNull);
    });
  });
}
