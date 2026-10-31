import 'dart:convert';

import '../core/money.dart';

/// One row of the audit log, for the History > Activity list.
class ActivityItem {
  const ActivityItem({
    required this.id,
    required this.entity,
    required this.entityId,
    required this.action,
    this.details,
    required this.at,
  });

  final String id;
  final String entity;
  final String entityId;
  final String action;
  final Map<String, Object?>? details;
  final int at;

  factory ActivityItem.fromMap(Map<String, Object?> m) {
    Map<String, Object?>? details;
    final raw = m['details'];
    if (raw is String && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw);
        if (decoded is Map) details = Map<String, Object?>.from(decoded);
      } catch (_) {
        details = null;
      }
    }
    return ActivityItem(
      id: m['id'] as String,
      entity: m['entity'] as String,
      entityId: m['entity_id'] as String,
      action: m['action'] as String,
      details: details,
      at: m['at'] as int,
    );
  }
}

String _methodText(Object? v) => switch (v) {
      'cash' => 'Cash',
      'upi' => 'UPI',
      _ => 'Other',
    };

/// Human-readable one-liner (or a few lines for edits, with before/after).
String describeActivity(ActivityItem a) {
  final d = a.details ?? const <String, Object?>{};
  final name = d['name'] ?? d['customer_name'];
  final who = name == null ? '' : ' - $name';

  switch ('${a.entity}.${a.action}') {
    case 'customer.create':
      return 'Customer add kiya$who';
    case 'customer.update':
      final lines = <String>['Customer edit kiya$who'];
      final changes = d['changes'];
      if (changes is Map) {
        changes.forEach((k, v) {
          if (v is Map) {
            lines.add('$k: ${v['before'] ?? '-'} → ${v['after'] ?? '-'}');
          }
        });
      }
      return lines.join('\n');
    case 'customer.archive':
      return 'Customer archive kiya$who';
    case 'customer.unarchive':
      return 'Customer archive se wapas aaya$who';
    case 'customer.delete':
      return 'Customer Trash me gaya$who';
    case 'customer.restore':
      return 'Customer Trash se wapas aaya$who';
    case 'customer.purge':
      return 'Customer hamesha ke liye delete hua';
    case 'transaction.create':
      final type = d['type'] == 'udhaari' ? 'Udhaari' : 'Jama';
      final amt = d['amount_paise'];
      final amount = amt is num ? formatPaise(amt.toInt()) : '';
      final method =
          d['type'] == 'jama' ? ' (${_methodText(d['method'])})' : '';
      return '$type $amount$method$who';
    case 'transaction.reverse':
      final amt = d['amount_paise'];
      final amount = amt is num ? formatPaise(amt.toInt()) : '';
      return 'Galti sudhari (Reverse) $amount$who';
    case 'system.erase':
      return 'Saara data is phone se delete kiya gaya';
    default:
      return '${a.entity} ${a.action}';
  }
}
