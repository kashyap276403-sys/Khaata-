import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/data/models.dart';
import 'package:khaata/voice/voice_parser.dart';

Customer _c(int seq, String name) => Customer(
      id: 'id$seq',
      seq: seq,
      name: name,
      createdAt: 0,
      updatedAt: 0,
    );

void main() {
  final customers = [
    _c(1, 'Ramesh Kumar'),
    _c(2, 'Suresh'),
    _c(3, 'Mohan Lal'),
    _c(4, 'Mohan Lal'),
  ];

  VoiceCommand parse(String s) => parseVoiceCommand(s, customers: customers);

  test('udhaari with digits', () {
    final r = parse('Ramesh ke 500 rupaye udhaari');
    expect(r.type, TxnType.udhaari);
    expect(r.amountPaise, 50000);
    expect(r.candidates.map((e) => e.id), ['id1']);
  });

  test('jama with UPI', () {
    final r = parse('Suresh ne 300 rupaye UPI se jama kiye');
    expect(r.type, TxnType.jama);
    expect(r.amountPaise, 30000);
    expect(r.method, PayMethod.upi);
    expect(r.candidates.map((e) => e.id), ['id2']);
  });

  test('method is null when not spoken', () {
    final r = parse('Suresh ne 100 rupaye jama kiye');
    expect(r.method, isNull);
  });

  test('Hindi number words', () {
    final r = parse('Ramesh ko pachas rupaye udhaari');
    expect(r.amountPaise, 5000);
  });

  test('dedh sau', () {
    final r = parse('Ramesh ko dedh sau rupaye udhaari');
    expect(r.amountPaise, 15000);
  });

  test('duplicate names give two candidates, never auto-picked', () {
    final r = parse('Mohan Lal ke 200 rupaye udhaari');
    expect(r.candidates.length, 2);
  });

  test('unknown customer gives no candidates and an issue', () {
    final r = parse('Zebra ke 200 rupaye udhaari');
    expect(r.candidates, isEmpty);
    expect(r.issues, isNotEmpty);
  });

  test('missing amount is reported, not guessed', () {
    final r = parse('Ramesh udhaari');
    expect(r.amountPaise, isNull);
    expect(r.issues, isNotEmpty);
  });

  test('conflicting type is left empty', () {
    final r = parse('Ramesh 100 rupaye udhaari jama');
    expect(r.type, isNull);
    expect(r.issues, isNotEmpty);
  });

  test('empty sentence changes nothing', () {
    final r = parse('   ');
    expect(r.type, isNull);
    expect(r.amountPaise, isNull);
    expect(r.candidates, isEmpty);
  });
}
