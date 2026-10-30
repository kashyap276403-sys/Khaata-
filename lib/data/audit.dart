import 'dart:convert';

import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';

/// Appends one audit record. Must be called inside the same SQLite
/// transaction as the change it describes.
Future<void> writeAudit(
  DatabaseExecutor ex, {
  required String entity,
  required String entityId,
  required String action,
  Map<String, Object?>? details,
  required int at,
}) async {
  await ex.insert('audit_log', {
    'id': const Uuid().v4(),
    'entity': entity,
    'entity_id': entityId,
    'action': action,
    'details': details == null ? null : jsonEncode(details),
    'at': at,
  });
}
