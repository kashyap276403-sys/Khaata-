import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

/// SQLite setup.
///
/// SOURCE OF TRUTH FOR BALANCES: the `transactions` table.
///   balance(customer) = SUM(udhaari paise) - SUM(jama paise)
///   over rows where deleted_at IS NULL (reversals are normal rows of the
///   opposite type). No balance is stored anywhere else.
class AppDatabase {
  AppDatabase._();

  static const int schemaVersion = 1;

  static Future<Database> open({String? path, DatabaseFactory? factory}) async {
    final f = factory ?? databaseFactory;
    final dbPath = path ?? p.join(await f.getDatabasesPath(), 'khaata.db');
    return f.openDatabase(
      dbPath,
      options: OpenDatabaseOptions(
        version: schemaVersion,
        singleInstance: false,
        onConfigure: _onConfigure,
        onCreate: _onCreate,
        onUpgrade: _onUpgrade,
      ),
    );
  }

  static Future<void> _onConfigure(Database db) async {
    await db.execute('PRAGMA foreign_keys = ON');
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
CREATE TABLE customers(
  id TEXT PRIMARY KEY,
  seq INTEGER NOT NULL UNIQUE,
  name TEXT NOT NULL CHECK(length(trim(name)) > 0),
  name_normalized TEXT NOT NULL,
  name_skeleton TEXT NOT NULL,
  phone TEXT,
  phone2 TEXT,
  address TEXT,
  notes TEXT,
  due_date INTEGER,
  archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0, 1)),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
)''');
    await db.execute('CREATE INDEX idx_customers_phone ON customers(phone)');
    await db.execute(
        'CREATE INDEX idx_customers_name ON customers(name_normalized)');

    await db.execute('''
CREATE TABLE transactions(
  id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(id),
  type TEXT NOT NULL CHECK(type IN ('udhaari', 'jama')),
  amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
  method TEXT NOT NULL CHECK(method IN ('cash', 'upi', 'other')),
  note TEXT,
  created_at INTEGER NOT NULL,
  idempotency_key TEXT UNIQUE,
  reverses_id TEXT REFERENCES transactions(id),
  deleted_at INTEGER
)''');
    await db.execute(
        'CREATE UNIQUE INDEX idx_txn_reverses ON transactions(reverses_id) WHERE reverses_id IS NOT NULL');
    await db.execute(
        'CREATE INDEX idx_txn_customer ON transactions(customer_id, created_at)');

    await db.execute('''
CREATE TABLE audit_log(
  id TEXT PRIMARY KEY,
  entity TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  action TEXT NOT NULL,
  details TEXT,
  at INTEGER NOT NULL
)''');
    await db.execute(
        'CREATE INDEX idx_audit_entity ON audit_log(entity, entity_id)');
  }

  static Future<void> _onUpgrade(Database db, int oldV, int newV) async {
    // Migrations go here, one `if (oldV < N)` block per schema version.
    // Never drop or rewrite user data in a migration.
  }
}
