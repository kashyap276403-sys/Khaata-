import 'package:sqflite/sqflite.dart';

import 'change_bus.dart';
import 'models.dart';

class ReminderItem {
  const ReminderItem({
    required this.customer,
    required this.balancePaise,
    required this.dueDate,
    required this.daysOverdue,
  });

  final Customer customer;
  final int balancePaise;

  /// Start of the due day, epoch millis (local time).
  final int dueDate;

  /// 0 = due today, 1+ = days past the due date.
  final int daysOverdue;
}

/// In-app due-date reminders. They need no notification permission, so they
/// keep working even if notifications are denied.
///
/// A customer is listed when: not deleted, not archived, has a due date that
/// is today or earlier, still owes money, and is neither snoozed nor
/// dismissed for that due date.
class ReminderRepository {
  ReminderRepository(this._db, this._bus);

  final Database _db;
  final ChangeBus _bus;

  Future<List<ReminderItem>> due({DateTime? now}) async {
    final n = now ?? DateTime.now();
    final startToday = DateTime(n.year, n.month, n.day).millisecondsSinceEpoch;
    final startTomorrow =
        DateTime(n.year, n.month, n.day + 1).millisecondsSinceEpoch;

    final rows = await _db.rawQuery('''
SELECT c.*, COALESCE(b.bal, 0) AS balance
FROM customers c
LEFT JOIN (
  SELECT customer_id,
         SUM(CASE type WHEN 'udhaari' THEN amount_paise ELSE -amount_paise END) AS bal
  FROM transactions WHERE deleted_at IS NULL GROUP BY customer_id
) b ON b.customer_id = c.id
LEFT JOIN reminder_state r ON r.customer_id = c.id
WHERE c.deleted_at IS NULL AND c.archived = 0
  AND c.due_date IS NOT NULL AND c.due_date < ?
  AND COALESCE(b.bal, 0) > 0
  AND (r.snoozed_until IS NULL OR r.snoozed_until <= ?)
  AND (r.dismissed_due IS NULL OR r.dismissed_due <> c.due_date)
ORDER BY c.due_date, c.name_normalized''',
        [startTomorrow, n.millisecondsSinceEpoch]);

    return rows.map((r) {
      final c = Customer.fromMap(r);
      final overdue = (startToday - c.dueDate!) ~/ Duration.millisecondsPerDay;
      return ReminderItem(
        customer: c,
        balancePaise: r['balance'] as int,
        dueDate: c.dueDate!,
        daysOverdue: overdue < 0 ? 0 : overdue,
      );
    }).toList();
  }

  /// Hide this reminder for [days] days.
  Future<void> snooze(String customerId, {int days = 1, DateTime? now}) async {
    final n = now ?? DateTime.now();
    final until = n.add(Duration(days: days)).millisecondsSinceEpoch;
    await _db.rawInsert('''
INSERT INTO reminder_state(customer_id, snoozed_until) VALUES(?, ?)
ON CONFLICT(customer_id) DO UPDATE SET snoozed_until = excluded.snoozed_until''',
        [customerId, until]);
    _bus.notifyChanged();
  }

  /// Remove this reminder until the customer's due date is changed.
  Future<void> dismiss(String customerId) async {
    await _db.rawInsert('''
INSERT INTO reminder_state(customer_id, dismissed_due)
SELECT id, due_date FROM customers WHERE id = ?
ON CONFLICT(customer_id) DO UPDATE SET dismissed_due = excluded.dismissed_due''',
        [customerId]);
    _bus.notifyChanged();
  }
}
