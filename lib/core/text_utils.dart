/// Lowercase, trim and collapse repeated spaces.
String normalizeName(String s) =>
    s.trim().toLowerCase().replaceAll(RegExp(r'\s+'), ' ');

/// Keeps digits only and drops a leading India country code / trunk zero.
String normalizePhone(String s) {
  var d = s.replaceAll(RegExp(r'\D'), '');
  if (d.length == 12 && d.startsWith('91')) d = d.substring(2);
  if (d.length == 11 && d.startsWith('0')) d = d.substring(1);
  return d;
}

/// Rough Roman-spelling key so "Ramesh" and "Ramesha" look alike.
/// It is ONLY used to suggest possible duplicates; customers are never
/// merged automatically. Devanagari names are compared as-is.
String nameSkeleton(String name) {
  final n = normalizeName(name).replaceAll(' ', '');
  if (n.isEmpty) return '';
  final buf = StringBuffer();
  String last = '';
  for (var i = 0; i < n.length; i++) {
    final c = n[i];
    if (i > 0 && 'aeiouyh'.contains(c)) continue;
    if (c == last) continue;
    buf.write(c);
    last = c;
  }
  return buf.toString();
}

/// "dd/MM/yyyy HH:mm" in local time.
String formatDateTime(int epochMillis) {
  final d = DateTime.fromMillisecondsSinceEpoch(epochMillis);
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(d.day)}/${two(d.month)}/${d.year} ${two(d.hour)}:${two(d.minute)}';
}
