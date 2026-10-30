/// Money is ALWAYS stored as integer paise (never double).
/// Rs 50.50 == 5050 paise.
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
