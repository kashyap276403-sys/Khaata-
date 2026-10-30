import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/core/text_utils.dart';

void main() {
  test('normalizeName trims, lowercases and collapses spaces', () {
    expect(normalizeName('  Ramesh   KUMAR '), 'ramesh kumar');
  });

  test('normalizePhone keeps digits and drops +91 / leading 0', () {
    expect(normalizePhone('+91 98765-43210'), '9876543210');
    expect(normalizePhone('09876543210'), '9876543210');
    expect(normalizePhone('98765 43210'), '9876543210');
    expect(normalizePhone('abc'), '');
  });

  test('nameSkeleton groups similar Roman spellings', () {
    expect(nameSkeleton('Ramesh Kumar'), nameSkeleton('Ramesha Kumar'));
    expect(nameSkeleton('Suresh'), isNot(nameSkeleton('Ramesh')));
  });

  test('formatDateTime pads fields', () {
    final ms = DateTime(2026, 3, 5, 9, 7).millisecondsSinceEpoch;
    expect(formatDateTime(ms), '05/03/2026 09:07');
  });
}
