import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/app.dart';
import 'package:khaata/data/app_database.dart';
import 'package:khaata/services.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  testWidgets('shows home and navigates to customers', (tester) async {
    final db = await tester.runAsync(
      () => AppDatabase.open(
        path: inMemoryDatabasePath,
        factory: databaseFactoryFfi,
      ),
    );
    final services = AppServices.fromDatabase(db!);

    await tester.pumpWidget(AuraXApp(services: services));
    await tester.pump();

    expect(find.text('auraX Khaata'), findsWidgets);
    expect(find.text('Quick Entry'), findsWidgets);

    await tester.tap(find.text('Customers').last);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Naam, phone ya ID se khojein'), findsOneWidget);

    // Let in-flight queries finish before closing the database.
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pump();
    // Let sqflite's 10s lock-warning timer (fake clock) expire.
    await tester.pump(const Duration(seconds: 11));
    await tester.runAsync(() => db.close());
    await tester.pump(const Duration(seconds: 11));
  });
}
