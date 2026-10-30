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
}
