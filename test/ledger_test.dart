import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/core/money.dart';
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

  Future<Customer> newCustomer([String name = 'Ramesh']) =>
      customers.add(name: name);

  group('ledger arithmetic', () {
    test('partial and full payments', () async {
      final c = await newCustomer();
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 50000);
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.jama, amountPaise: 20000);
      expect(await customers.balanceFor(c.id), 30000);
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.jama, amountPaise: 30000);
      expect(await customers.balanceFor(c.id), 0);
    });

    test('paise precision: no floating point drift', () async {
      final c = await newCustomer();
      // 0.10 + 0.20 must be exactly 0.30
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 10);
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 20);
      expect(await customers.balanceFor(c.id), 30);
      final stored = parseRupeesToPaise('50.50')!;
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: stored);
      expect(stored, 5050);
      expect(await customers.balanceFor(c.id), 5080);
    });

    test('overpayment becomes negative balance (advance)', () async {
      final c = await newCustomer();
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.jama, amountPaise: 10000);
      expect(await customers.balanceFor(c.id), -10000);
    });

    test('amount limits are enforced', () async {
      final c = await newCustomer();
      await expectLater(
        ledger.addTransaction(
            customerId: c.id, type: TxnType.udhaari, amountPaise: 0),
        throwsA(isA<LedgerException>()),
      );
      await expectLater(
        ledger.addTransaction(
            customerId: c.id, type: TxnType.udhaari, amountPaise: -5),
        throwsA(isA<LedgerException>()),
      );
      await expectLater(
        ledger.addTransaction(
            customerId: c.id,
            type: TxnType.udhaari,
            amountPaise: maxAmountPaise + 1),
        throwsA(isA<LedgerException>()),
      );
      expect(await customers.balanceFor(c.id), 0);
    });
  });

  group('idempotency and atomicity', () {
    test('same key twice writes only one entry', () async {
      final c = await newCustomer();
      final a = await ledger.addTransaction(
        customerId: c.id,
        type: TxnType.udhaari,
        amountPaise: 10000,
        idempotencyKey: 'key-1',
      );
      final b = await ledger.addTransaction(
        customerId: c.id,
        type: TxnType.udhaari,
        amountPaise: 10000,
        idempotencyKey: 'key-1',
      );
      expect(b.id, a.id);
      expect(await customers.balanceFor(c.id), 10000);
      expect((await ledger.transactionsFor(c.id)).length, 1);
    });

    test('rapid double submit with one key creates one entry', () async {
      final c = await newCustomer();
      await Future.wait([
        for (var i = 0; i < 2; i++)
          ledger.addTransaction(
            customerId: c.id,
            type: TxnType.udhaari,
            amountPaise: 7000,
            idempotencyKey: 'tap',
          ),
      ]);
      expect(await customers.balanceFor(c.id), 7000);
    });

    test('failed write leaves nothing behind', () async {
      await expectLater(
        ledger.addTransaction(
            customerId: 'missing', type: TxnType.udhaari, amountPaise: 100),
        throwsA(isA<LedgerException>()),
      );
      final n = await db.rawQuery('SELECT COUNT(*) AS n FROM transactions');
      expect(n.first['n'], 0);
      final a = await db.rawQuery('SELECT COUNT(*) AS n FROM audit_log');
      expect(a.first['n'], 0);
    });
  });

  group('reversal and audit trail', () {
    test('reverse restores balance and keeps original entry', () async {
      final c = await newCustomer();
      final t = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 50000);
      final r = await ledger.reverse(t.id);
      expect(r.reversesId, t.id);
      expect(r.type, TxnType.jama);
      expect(await customers.balanceFor(c.id), 0);
      expect((await ledger.transactionsFor(c.id)).length, 2);
    });

    test('cannot reverse twice or reverse a reversal', () async {
      final c = await newCustomer();
      final t = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 50000);
      final r = await ledger.reverse(t.id);
      await expectLater(ledger.reverse(t.id), throwsA(isA<LedgerException>()));
      await expectLater(ledger.reverse(r.id), throwsA(isA<LedgerException>()));
      expect(await customers.balanceFor(c.id), 0);
    });

    test('audit log records create and reverse', () async {
      final c = await newCustomer();
      final t = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 100);
      await ledger.reverse(t.id);
      final rows = await db.query('audit_log',
          where: "entity = 'transaction'", orderBy: 'at, rowid');
      expect(rows.map((r) => r['action']).toList(), ['create', 'reverse']);
    });

    test('integrity check passes on a healthy ledger', () async {
      final c = await newCustomer();
      final t = await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 900);
      await ledger.reverse(t.id);
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.jama, amountPaise: 100);
      expect(await ledger.integrityIssues(), isEmpty);
    });
  });

  group('dashboard', () {
    test('totals, today window and pending count', () async {
      final a = await newCustomer('Asha');
      final b = await newCustomer('Bunty');
      final today = DateTime(2026, 10, 30, 10);
      final yesterday = DateTime(2026, 10, 29, 20);

      await ledger.addTransaction(
          customerId: a.id,
          type: TxnType.udhaari,
          amountPaise: 100000,
          now: yesterday);
      await ledger.addTransaction(
          customerId: a.id,
          type: TxnType.udhaari,
          amountPaise: 50000,
          now: today);
      await ledger.addTransaction(
          customerId: b.id,
          type: TxnType.udhaari,
          amountPaise: 20000,
          now: today);
      await ledger.addTransaction(
          customerId: b.id,
          type: TxnType.jama,
          amountPaise: 20000,
          now: today);

      final s = await ledger.dashboard(now: DateTime(2026, 10, 30, 15));
      expect(s.outstandingPaise, 150000);
      expect(s.todayUdhaariPaise, 70000);
      expect(s.todayJamaPaise, 20000);
      expect(s.customerCount, 2);
      expect(s.pendingCount, 1); // only Asha owes; Bunty is settled
    });
  });

  group('customers', () {
    test('delete excludes from totals; restore brings it back', () async {
      final c = await newCustomer();
      await ledger.addTransaction(
          customerId: c.id, type: TxnType.udhaari, amountPaise: 10000);

      await customers.softDelete(c.id);
      var s = await ledger.dashboard();
      expect(s.outstandingPaise, 0);
      expect(s.customerCount, 0);
      expect(await customers.list(), isEmpty);
      await expectLater(
        ledger.addTransaction(
            customerId: c.id, type: TxnType.udhaari, amountPaise: 100),
        throwsA(isA<LedgerException>()),
      );

      await customers.restore(c.id);
      s = await ledger.dashboard();
      expect(s.outstandingPaise, 10000);
      expect(s.customerCount, 1);
      // restoring twice must not duplicate anything
      await expectLater(
          customers.restore(c.id), throwsA(isA<LedgerException>()));
    });

    test('archive hides from active list only', () async {
      final c = await newCustomer();
      await customers.setArchived(c.id, true);
      expect(await customers.list(), isEmpty);
      expect((await customers.list(archived: true)).length, 1);
    });

    test('two customers with the same name stay separate', () async {
      final a = await newCustomer('Ramesh');
      final b = await newCustomer('Ramesh');
      expect(a.id, isNot(b.id));
      expect(a.code, isNot(b.code));
      expect((await customers.list()).length, 2);
    });

    test('possible duplicates are suggested, never merged', () async {
      final a = await customers.add(name: 'Ramesh Kumar', phone: '98765 43210');
      final byName = await customers.findPossibleMatches(name: 'Ramesha Kumar');
      expect(byName.map((c) => c.id), [a.id]);
      final byPhone = await customers.findPossibleMatches(
          name: 'Totally Different', phone: '+91 9876543210');
      expect(byPhone.map((c) => c.id), [a.id]);
      final none = await customers.findPossibleMatches(name: 'Suresh');
      expect(none, isEmpty);
      final self = await customers.findPossibleMatches(
          name: 'Ramesh Kumar', excludeId: a.id);
      expect(self, isEmpty);
    });

    test('search by name, phone and customer code', () async {
      await customers.add(name: 'Ramesh', phone: '9876543210');
      await customers.add(name: 'Suresh', phone: '9123456789');
      expect((await customers.list(query: 'rame')).length, 1);
      expect((await customers.list(query: '9876543')).length, 1);
      expect((await customers.list(query: 'C0002')).single.customer.name,
          'Suresh');
      expect(await customers.list(query: 'zzz'), isEmpty);
      expect(await customers.list(query: '%'), isEmpty);
    });

    test('update writes before/after to audit log', () async {
      final c = await newCustomer('Ramesh');
      await customers.update(c.id, name: 'Ramesh Kumar');
      final updated = await customers.getById(c.id);
      expect(updated!.name, 'Ramesh Kumar');
      final rows = await db.query('audit_log',
          where: "entity = 'customer' AND action = 'update'");
      expect(rows.length, 1);
      expect(rows.first['details'] as String, contains('Ramesh Kumar'));
    });

    test('empty name and bad phone are rejected', () async {
      await expectLater(customers.add(name: '   '), throwsArgumentError);
      await expectLater(
          customers.add(name: 'X', phone: '123'), throwsArgumentError);
    });
  });
}
