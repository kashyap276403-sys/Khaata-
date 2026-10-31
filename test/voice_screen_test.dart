import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/app.dart';
import 'package:khaata/data/app_database.dart';
import 'package:khaata/screens/voice_entry_screen.dart';
import 'package:khaata/services.dart';
import 'package:khaata/voice/speech_service.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class FakeSpeech implements SpeechService {
  @override
  Future<bool> initialize(void Function(String message) onError) async => false;

  @override
  Future<void> start({
    required String localeId,
    required void Function(String text) onText,
    required void Function() onDone,
  }) async {}

  @override
  Future<void> stop() async {}
}

void main() {
  sqfliteFfiInit();

  testWidgets('typed sentence is previewed and saved only after confirm',
      (tester) async {
    tester.view.physicalSize = const Size(1200, 4000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    final db = await tester.runAsync(
      () => AppDatabase.open(
        path: inMemoryDatabasePath,
        factory: databaseFactoryFfi,
      ),
    );
    final services = AppServices.fromDatabase(db!, speech: FakeSpeech());
    await tester.runAsync(() => services.customers.add(name: 'Ramesh'));

    await tester.pumpWidget(
      AuraXApp(services: services, home: const VoiceEntryScreen()),
    );
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pump();

    await tester.enterText(
        find.byType(TextField).first, 'Ramesh ke 500 rupaye udhaari');
    await tester.tap(find.text('Samjhein (preview dikhayein)'));
    await tester.pump();

    expect(find.text('Preview'), findsOneWidget);
    expect(find.text('Confirm aur Save'), findsOneWidget);

    // Nothing saved yet.
    final before = await tester.runAsync(() => services.ledger.dashboard());
    expect(before!.outstandingPaise, 0);

    await tester.ensureVisible(find.text('Confirm aur Save'));
    await tester.tap(find.text('Confirm aur Save'));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await tester.pump();

    final after = await tester.runAsync(() => services.ledger.dashboard());
    expect(after!.outstandingPaise, 50000);

    await tester.runAsync(() => db.close());
  });

  testWidgets('mic unavailable shows a message and typing still works',
      (tester) async {
    final db = await tester.runAsync(
      () => AppDatabase.open(
        path: inMemoryDatabasePath,
        factory: databaseFactoryFfi,
      ),
    );
    final services = AppServices.fromDatabase(db!, speech: FakeSpeech());

    await tester.pumpWidget(
      AuraXApp(services: services, home: const VoiceEntryScreen()),
    );
    await tester.pump();

    await tester.tap(find.text('Bolna shuru karein'));
    await tester.pump();
    expect(find.textContaining('ijazat'), findsOneWidget);
    expect(find.byType(TextField), findsWidgets);

    await tester.runAsync(() => db.close());
  });
}
