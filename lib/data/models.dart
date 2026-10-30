enum TxnType {
  udhaari,
  jama;

  static TxnType fromDb(String v) =>
      TxnType.values.firstWhere((e) => e.name == v);

  TxnType get opposite =>
      this == TxnType.udhaari ? TxnType.jama : TxnType.udhaari;
}

/// Label only. The app never moves money.
enum PayMethod {
  cash,
  upi,
  other;

  static PayMethod fromDb(String v) =>
      PayMethod.values.firstWhere((e) => e.name == v);
}

class Customer {
  const Customer({
    required this.id,
    required this.seq,
    required this.name,
    this.phone,
    this.phone2,
    this.address,
    this.notes,
    this.dueDate,
    this.archived = false,
    required this.createdAt,
    required this.updatedAt,
    this.deletedAt,
  });

  /// Stable UUID. Names are never used as keys.
  final String id;

  /// Human-friendly running number, shown as code (C0001).
  final int seq;
  final String name;
  final String? phone;
  final String? phone2;
  final String? address;
  final String? notes;

  /// Epoch millis (local due date), optional.
  final int? dueDate;
  final bool archived;
  final int createdAt;
  final int updatedAt;
  final int? deletedAt;

  String get code => 'C${seq.toString().padLeft(4, '0')}';

  factory Customer.fromMap(Map<String, Object?> m) => Customer(
        id: m['id'] as String,
        seq: m['seq'] as int,
        name: m['name'] as String,
        phone: m['phone'] as String?,
        phone2: m['phone2'] as String?,
        address: m['address'] as String?,
        notes: m['notes'] as String?,
        dueDate: m['due_date'] as int?,
        archived: (m['archived'] as int) == 1,
        createdAt: m['created_at'] as int,
        updatedAt: m['updated_at'] as int,
        deletedAt: m['deleted_at'] as int?,
      );
}

class CustomerWithBalance {
  const CustomerWithBalance(this.customer, this.balancePaise);
  final Customer customer;

  /// > 0 : customer owes the shopkeeper. < 0 : advance paid by customer.
  final int balancePaise;
}

class LedgerTxn {
  const LedgerTxn({
    required this.id,
    required this.customerId,
    required this.type,
    required this.amountPaise,
    required this.method,
    this.note,
    required this.createdAt,
    this.idempotencyKey,
    this.reversesId,
  });

  final String id;
  final String customerId;
  final TxnType type;

  /// Always a positive integer number of paise.
  final int amountPaise;
  final PayMethod method;
  final String? note;
  final int createdAt;
  final String? idempotencyKey;

  /// Set when this entry reverses another entry.
  final String? reversesId;

  bool get isReversal => reversesId != null;

  factory LedgerTxn.fromMap(Map<String, Object?> m) => LedgerTxn(
        id: m['id'] as String,
        customerId: m['customer_id'] as String,
        type: TxnType.fromDb(m['type'] as String),
        amountPaise: m['amount_paise'] as int,
        method: PayMethod.fromDb(m['method'] as String),
        note: m['note'] as String?,
        createdAt: m['created_at'] as int,
        idempotencyKey: m['idempotency_key'] as String?,
        reversesId: m['reverses_id'] as String?,
      );
}

class RecentTxn {
  const RecentTxn(this.txn, this.customerName);
  final LedgerTxn txn;
  final String customerName;
}

class DashboardSummary {
  const DashboardSummary({
    required this.outstandingPaise,
    required this.todayUdhaariPaise,
    required this.todayJamaPaise,
    required this.customerCount,
    required this.pendingCount,
  });

  static const DashboardSummary empty = DashboardSummary(
    outstandingPaise: 0,
    todayUdhaariPaise: 0,
    todayJamaPaise: 0,
    customerCount: 0,
    pendingCount: 0,
  );

  final int outstandingPaise;
  final int todayUdhaariPaise;
  final int todayJamaPaise;
  final int customerCount;

  /// Customers whose balance is greater than zero.
  final int pendingCount;
}

class LedgerException implements Exception {
  LedgerException(this.message);
  final String message;

  @override
  String toString() => message;
}
