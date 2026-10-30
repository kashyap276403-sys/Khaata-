import 'package:flutter_test/flutter_test.dart';
import 'package:khaata/app.dart';

void main() {
  testWidgets('shows home and navigates between tabs', (tester) async {
    await tester.pumpWidget(const AuraXApp());

    expect(find.text('auraX Khaata'), findsWidgets);
    expect(find.text('Total baaki (Outstanding)'), findsOneWidget);

    await tester.tap(find.text('Customers').last);
    await tester.pumpAndSettle();
    expect(find.text('Abhi koi customer nahi'), findsOneWidget);
  });
}
