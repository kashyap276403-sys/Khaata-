import 'package:flutter/widgets.dart';
import 'package:sqflite/sqflite.dart';

import 'data/change_bus.dart';
import 'data/customer_repository.dart';
import 'data/export_repository.dart';
import 'data/ledger_repository.dart';
import 'data/reminder_repository.dart';
import 'data/report_repository.dart';
import 'voice/speech_service.dart';

/// Everything the UI needs from the data layer.
class AppServices {
  AppServices._(this.db, this.bus, this.customers, this.ledger, this.reports,
      this.reminders, this.exports, this.speech);

  factory AppServices.fromDatabase(Database db, {SpeechService? speech}) {
    final bus = ChangeBus();
    return AppServices._(
      db,
      bus,
      CustomerRepository(db, bus),
      LedgerRepository(db, bus),
      ReportRepository(db),
      ReminderRepository(db, bus),
      ExportRepository(db, bus),
      speech ?? DeviceSpeechService(),
    );
  }

  final Database db;
  final ChangeBus bus;
  final CustomerRepository customers;
  final LedgerRepository ledger;
  final ReportRepository reports;
  final ReminderRepository reminders;
  final ExportRepository exports;
  final SpeechService speech;
}

class ServicesScope extends InheritedWidget {
  const ServicesScope(
      {super.key, required this.services, required super.child});

  final AppServices services;

  static AppServices of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<ServicesScope>();
    assert(scope != null, 'ServicesScope missing above this widget');
    return scope!.services;
  }

  @override
  bool updateShouldNotify(ServicesScope oldWidget) =>
      services != oldWidget.services;
}
