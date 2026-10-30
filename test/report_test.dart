import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/data/app_database.dart';
import 'package:khaata/data/change_bus.dart';
import 'package:khaata/data/customer_repository.dart';
import 'package:khaata/data/ledger_repository.dart';
import 'package:khaata/data/models.dart';
import 'package:khaata/data/report_repository.dart';
import 'package:khaata/reports/exporter.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  late Database db;
  late CustomerRepository customers;
  late LedgerRepository ledger;
  late ReportRepository reports;

  int ms(DateTime d) => d.millisecondsSinceEpoch;

  setUp(() async {
    db = await AppDatabase.open(
      path: inMemoryDatabasePath,
      factory: databaseFactoryFfi,
    );
    final bus = ChangeBus();
    customers = CustomerRepository(db, bus);
    ledger = LedgerRepository(db, bus);
    reports = ReportRepository(db);
  });

  tearDown(() async {
    await db.close();
  });

  Future<(Customer, Customer)> seed() async {
    final a = await customers.add(name: 'Asha');
    final b = await customers.add(name: 'Bunty');
    // before the period
    await ledger.addTransaction(
        customerId: a.id,
        type: TxnType.udhaari,
        amountPaise: 100000,
        now: DateTime(2026, 9, 20, 10));
    // inside Oct 2026
    await ledger.addTransaction(
        customerId: a.id,
        type: TxnType.udhaari,
        amountPaise: 50050,
        now: DateTime(2026, 10, 5, 10));
    await ledger.addTransaction(
        customerId: a.id,
        type: TxnType.jama,
        amountPaise: 30000,
        method: PayMethod.upi,
        now: DateTime(2026, 10, 6, 10));
    await ledger.addTransaction(
        customerId: b.id,
        type: TxnType.jama,
        amountPaise: 2500,
        method: PayMethod.cash,
        now: DateTime(2026, 10, 7, 10));
    return (a, b);
  }

  group('period report', () {
    test('totals, method split and closing balance match the ledger', () async {
      final (a, b) = await seed();
      final s = await reports.summary(
        fromMs: ms(DateTime(2026, 10, 1)),
        toMs: ms(DateTime(2026, 11, 1)),
      );
      expect(s.udhaariPaise, 50050);
      expect(s.jamaPaise, 32500);
      expect(s.netPaise, 17550);
      expect(s.jamaUpiPaise, 30000);
      expect(s.jamaCashPaise, 2500);
      expect(s.jamaOtherPaise, 0);
      expect(s.entryCount, 3);

      // Closing outstanding equals the sum of customer balances (ledger rule).
      final ba = await customers.balanceFor(a.id);
      final bb = await customers.balanceFor(b.id);
      expect(s.closingOutstandingPaise, ba + bb);
      expect(s.rows.fold<int>(0, (t, r) => t + r.closingBalancePaise), ba + bb);
      expect(s.rows.map((r) => r.name), ['Asha', 'Bunty']);
    });

    test('customers without activity in the period are not listed', () async {
      await seed();
      final s = await reports.summary(
        fromMs: ms(DateTime(2026, 9, 1)),
        toMs: ms(DateTime(2026, 10, 1)),
      );
      expect(s.rows.length, 1);
      expect(s.rows.single.name, 'Asha');
      expect(s.udhaariPaise, 100000);
    });

    test('reversals are counted in totals but not in the method split',
        () async {
      final c = await customers.add(name: 'Asha');
      final t = await ledger.addTransaction(
          customerId: c.id,
          type: TxnType.udhaari,
          amountPaise: 10000,
          now: DateTime(2026, 10, 2, 9));
      await ledger.reverse(t.id, now: DateTime(2026, 10, 2, 10));
      final s = await reports.summary(
        fromMs: ms(DateTime(2026, 10, 1)),
        toMs: ms(DateTime(2026, 11, 1)),
      );
      expect(s.udhaariPaise, 10000);
      expect(s.jamaPaise, 10000);
      expect(s.netPaise, 0);
      expect(s.reversalCount, 1);
      expect(s.jamaCashPaise + s.jamaUpiPaise + s.jamaOtherPaise, 0);
      expect(s.closingOutstandingPaise, 0);
    });

    test('deleted customers are excluded', () async {
      final (a, _) = await seed();
      await customers.softDelete(a.id);
      final s = await reports.summary(
        fromMs: ms(DateTime(2026, 10, 1)),
        toMs: ms(DateTime(2026, 11, 1)),
      );
      expect(s.udhaariPaise, 0);
      expect(s.jamaPaise, 2500);
    });
  });

  group('customer statement', () {
    test('opening, running balance and closing', () async {
      final (a, _) = await seed();
      final st = (await reports.statement(
        customerId: a.id,
        fromMs: ms(DateTime(2026, 10, 1)),
        toMs: ms(DateTime(2026, 11, 1)),
      ))!;
      expect(st.openingPaise, 100000);
      expect(st.lines.map((l) => l.runningBalancePaise), [150050, 120050]);
      expect(st.closingPaise, 120050);
      expect(st.closingPaise, await customers.balanceFor(a.id));
    });

    test('unknown or deleted customer returns null', () async {
      final (a, _) = await seed();
      expect(await reports.statement(customerId: 'nope', fromMs: 0, toMs: 1),
          isNull);
      await customers.softDelete(a.id);
      expect(await reports.statement(customerId: a.id, fromMs: 0, toMs: 1),
          isNull);
    });
  });

  group('exports', () {
    test('rupeesPlain uses integer maths', () {
      expect(rupeesPlain(5050), '50.50');
      expect(rupeesPlain(5), '0.05');
      expect(rupeesPlain(100000), '1000.00');
      expect(rupeesPlain(-250), '-2.50');
    });

    test('csvCell quotes and neutralises formulas', () {
      expect(csvCell('Ramesh'), 'Ramesh');
      expect(csvCell('A, B'), '"A, B"');
      expect(csvCell('say "hi"'), '"say ""hi"""');
      expect(csvCell('=SUM(A1)'), "'=SUM(A1)");
      expect(csvCell('@cmd'), "'@cmd");
      expect(csvCell(null), '');
    });

    test('report CSV totals equal the report numbers', () async {
      await seed();
      final s = await reports.summary(
        fromMs: ms(DateTime(2026, 10, 1)),
        toMs: ms(DateTime(2026, 11, 1)),
      );
      final csv = buildReportCsv(s, 'Oct 2026');
      expect(csv, contains('Total Udhaari (Rs),500.50'));
      expect(csv, contains('Total Jama (Rs),325.00'));
      expect(csv, contains('Jama via UPI (Rs),300.00'));
      expect(csv, contains('Asha'));
      expect(csv, contains('C0001,Asha,500.50,300.00,1200.50'));
    });

    test('statement CSV has running balance and closing line', () async {
      final (a, _) = await seed();
      final st = (await reports.statement(
        customerId: a.id,
        fromMs: ms(DateTime(2026, 10, 1)),
        toMs: ms(DateTime(2026, 11, 1)),
      ))!;
      final csv = buildStatementCsv(st, 'Oct 2026');
      expect(csv, contains('Opening balance (Rs),1000.00'));
      expect(csv, contains('Closing balance (Rs),1200.50'));
      expect(csv, contains('UPI'));
      // Udhaari rows carry no payment method.
      final udhaariLine =
          csv.split('\r\n').firstWhere((l) => l.contains(',Udhaari,'));
      expect(
          udhaariLine.contains('Cash') || udhaariLine.contains('UPI'), isFalse);
    });

    test('PDFs are generated and Hindi text does not crash', () async {
      final c = await customers.add(name: 'रमेश');
      await ledger.addTransaction(
          customerId: c.id,
          type: TxnType.udhaari,
          amountPaise: 12345,
          now: DateTime(2026, 10, 2));
      final from = ms(DateTime(2026, 10, 1));
      final to = ms(DateTime(2026, 11, 1));
      final s = await reports.summary(fromMs: from, toMs: to);
      final pdf = await buildReportPdf(s, 'Oct 2026');
      expect(String.fromCharCodes(pdf.take(4)), '%PDF');
      final st =
          (await reports.statement(customerId: c.id, fromMs: from, toMs: to))!;
      final pdf2 = await buildStatementPdf(st, 'Oct 2026');
      expect(String.fromCharCodes(pdf2.take(4)), '%PDF');
      expect(pdfSafe('रमेश Kumar'), '???? Kumar');
      expect(pdfMoney(123456), 'Rs 1,234.56');
    });
  });
}
