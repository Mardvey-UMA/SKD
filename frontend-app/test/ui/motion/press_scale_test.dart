import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/motion/press_scale.dart';

void main() {
  group('PressScale', () {
    testWidgets('onTap fires exactly once per tap', (tester) async {
      int taps = 0;

      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: Center(
            child: PressScale(
              onTap: () => taps++,
              child: const SizedBox(
                width: 80,
                height: 40,
                child: Text('tap'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.byType(PressScale));
      await tester.pumpAndSettle();
      expect(taps, 1);

      await tester.tap(find.byType(PressScale));
      await tester.pumpAndSettle();
      expect(taps, 2);
    });

    testWidgets('AnimatedScale scale changes on press', (tester) async {
      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: Center(
            child: PressScale(
              onTap: () {},
              child: const SizedBox(width: 80, height: 40),
            ),
          ),
        ),
      );

      AnimatedScale animated() =>
          tester.widget<AnimatedScale>(find.byType(AnimatedScale));

      expect(animated().scale, 1.0);

      final TestGesture gesture = await tester.startGesture(
        tester.getCenter(find.byType(PressScale)),
      );
      await tester.pump();
      expect(animated().scale, 0.92);

      await gesture.up();
      await tester.pumpAndSettle();
      expect(animated().scale, 1.0);
    });

    testWidgets('respects custom scale and duration', (tester) async {
      const customScale = 0.8;
      const customDuration = Duration(milliseconds: 300);

      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: Center(
            child: PressScale(
              onTap: () {},
              scale: customScale,
              duration: customDuration,
              child: const SizedBox(width: 80, height: 40),
            ),
          ),
        ),
      );

      final AnimatedScale initial =
          tester.widget<AnimatedScale>(find.byType(AnimatedScale));
      expect(initial.duration, customDuration);

      final TestGesture gesture = await tester.startGesture(
        tester.getCenter(find.byType(PressScale)),
      );
      await tester.pump();
      final AnimatedScale pressed =
          tester.widget<AnimatedScale>(find.byType(AnimatedScale));
      expect(pressed.scale, customScale);

      await gesture.up();
      await tester.pumpAndSettle();
    });
  });
}
