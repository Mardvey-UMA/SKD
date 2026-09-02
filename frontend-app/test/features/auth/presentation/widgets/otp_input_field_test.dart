import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/auth/presentation/widgets/otp_input_field.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  group('OtpInputField', () {
    testWidgets('renders 6 TextField cells', (tester) async {
      await tester.pumpWidget(
        _wrap(
          OtpInputField(
            onChanged: (_) {},
            onCompleted: (_) {},
          ),
        ),
      );
      expect(find.byType(TextField), findsNWidgets(6));
    });

    testWidgets('entering a digit fires onChanged with current value',
        (tester) async {
      String? lastChange;
      await tester.pumpWidget(
        _wrap(
          OtpInputField(
            onChanged: (v) => lastChange = v,
            onCompleted: (_) {},
          ),
        ),
      );
      await tester.enterText(find.byType(TextField).first, '3');
      await tester.pump();
      expect(lastChange, '3');
    });

    testWidgets('onCompleted fires when 6th digit entered', (tester) async {
      String? completed;
      await tester.pumpWidget(
        _wrap(
          OtpInputField(
            onChanged: (_) {},
            onCompleted: (v) => completed = v,
          ),
        ),
      );

      final fields = find.byType(TextField);
      await tester.enterText(fields.at(0), '1');
      await tester.pump();
      await tester.enterText(fields.at(1), '2');
      await tester.pump();
      await tester.enterText(fields.at(2), '3');
      await tester.pump();
      await tester.enterText(fields.at(3), '4');
      await tester.pump();
      await tester.enterText(fields.at(4), '5');
      await tester.pump();
      await tester.enterText(fields.at(5), '6');
      await tester.pump();

      expect(completed, '123456');
    });

    testWidgets('only digits are accepted (non-digit is filtered)',
        (tester) async {
      String? lastChange;
      await tester.pumpWidget(
        _wrap(
          OtpInputField(
            onChanged: (v) => lastChange = v,
            onCompleted: (_) {},
          ),
        ),
      );
      await tester.enterText(find.byType(TextField).first, 'a');
      await tester.pump();
      // Non-digit filtered → empty string (or empty)
      expect(lastChange == null || lastChange!.isEmpty, isTrue);
    });

    testWidgets('error state: hasError=true should not throw', (tester) async {
      await tester.pumpWidget(
        _wrap(
          OtpInputField(
            onChanged: (_) {},
            onCompleted: (_) {},
            hasError: true,
          ),
        ),
      );
      expect(find.byType(TextField), findsNWidgets(6));
    });

    testWidgets('disabled: enabled=false renders 6 disabled cells',
        (tester) async {
      await tester.pumpWidget(
        _wrap(
          OtpInputField(
            onChanged: (_) {},
            onCompleted: (_) {},
            enabled: false,
          ),
        ),
      );
      final textFields = tester
          .widgetList<TextField>(find.byType(TextField))
          .toList();
      expect(textFields.every((f) => f.enabled == false), isTrue);
    });
  });
}
