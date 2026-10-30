/// Money is ALWAYS stored as integer paise (never double).
/// Rs 50.50 == 5050 paise.

/// Largest single entry allowed: Rs 10,00,00,000 (10 crore) = 1e10 paise.
const int maxAmountPaise = 10000000000;

String formatPaise(int paise) {
  final negative = paise < 0;
  final abs = paise.abs();
  final rupees = abs ~/ 100;
  final rem = abs % 100;

  final digits = rupees.toString();
  String grouped;
  if (digits.length <= 3) {
    grouped = digits;
  } else {
    final last3 = digits.substring(digits.length - 3);
    var rest = digits.substring(0, digits.length - 3);
    final parts = <String>[];
    while (rest.length > 2) {
      parts.insert(0, rest.substring(rest.length - 2));
      rest = rest.substring(0, rest.length - 2);
    }
    if (rest.isNotEmpty) parts.insert(0, rest);
    grouped = '${parts.join(',')},$last3';
  }

  final decimals = rem == 0 ? '' : '.${rem.toString().padLeft(2, '0')}';
  return '${negative ? '-' : ''}₹$grouped$decimals';
}

/// Parses user text like "50", "50.5", "1,000", "₹50.50" into paise.
/// Returns null for anything invalid (empty, negative, >2 decimals, letters).
/// Never uses floating point.
int? parseRupeesToPaise(String input) {
  final s = input.trim().replaceAll(',', '').replaceAll('₹', '').trim();
  if (s.isEmpty) return null;
  final m = RegExp(r'^(\d{1,9})(?:\.(\d{1,2}))?$').firstMatch(s);
  if (m == null) return null;
  final rupees = int.parse(m.group(1)!);
  final frac = m.group(2);
  final paise = frac == null ? 0 : int.parse(frac.padRight(2, '0'));
  return rupees * 100 + paise;
}
