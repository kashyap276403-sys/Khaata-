import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/core/money.dart';

void main() {
  group('formatPaise', () {
    test('zero', () => expect(formatPaise(0), '₹0'));
    test('50.50 rupees is 5050 paise', () {
      expect(formatPaise(5050), '₹50.50');
    });
    test('small paise padded', () => expect(formatPaise(5), '₹0.05'));
    test('indian grouping', () {
      expect(formatPaise(100000), '₹1,000');
      expect(formatPaise(10000000), '₹1,00,000');
      expect(formatPaise(123456789), '₹12,34,567.89');
    });
    test('negative', () => expect(formatPaise(-25000), '-₹250'));
  });

  group('parseRupeesToPaise', () {
    test('whole and decimal amounts', () {
      expect(parseRupeesToPaise('50'), 5000);
      expect(parseRupeesToPaise('50.5'), 5050);
      expect(parseRupeesToPaise('50.50'), 5050);
      expect(parseRupeesToPaise('0.05'), 5);
      expect(parseRupeesToPaise('0.5'), 50);
    });
    test('commas, rupee sign and spaces are ignored', () {
      expect(parseRupeesToPaise(' ₹1,000 '), 100000);
      expect(parseRupeesToPaise('12,34,567.89'), 123456789);
    });
    test('invalid input returns null', () {
      expect(parseRupeesToPaise(''), isNull);
      expect(parseRupeesToPaise('abc'), isNull);
      expect(parseRupeesToPaise('-5'), isNull);
      expect(parseRupeesToPaise('50.555'), isNull);
      expect(parseRupeesToPaise('50.'), isNull);
      expect(parseRupeesToPaise('1e5'), isNull);
    });
    test('round trip with formatPaise', () {
      expect(formatPaise(parseRupeesToPaise('1234.56')!), '₹1,234.56');
    });
  });
}
