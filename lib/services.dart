import 'package:flutter/widgets.dart';
import 'package:sqflite/sqflite.dart';

import 'data/change_bus.dart';
import 'data/customer_repository.dart';
import 'data/ledger_repository.dart';
import 'data/report_repository.dart';

/// Everything the UI needs from the data layer.
class AppServices {
  AppServices._(this.db, this.bus, this.customers, this.ledger, this.reports);

  factory AppServices.fromDatabase(Database db) {
    final bus = ChangeBus();
    return AppServices._(
      db,
      bus,
      CustomerRepository(db, bus),
      LedgerRepository(db, bus),
      ReportRepository(db),
    );
  }

  final Database db;
  final ChangeBus bus;
  final CustomerRepository customers;
  final LedgerRepository ledger;
  final ReportRepository reports;
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
