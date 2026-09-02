import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/responsive/screen_fade.dart';
import 'package:frontend_app/ui/motion/page_transitions.dart';

void main() {
  group('ScreenFade', () {
    testWidgets('wraps an AnimatedSwitcher with 260ms default duration',
        (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ScreenFade(
            child: Text('a', key: ValueKey('a')),
          ),
        ),
      );

      final AnimatedSwitcher switcher =
          tester.widget(find.byType(AnimatedSwitcher));
      expect(switcher.duration, NFMotion.pageDuration);
    });

    testWidgets('swaps widgets without error', (tester) async {
      Widget buildWith(String label) => MaterialApp(
            home: ScreenFade(
              child: Text(label, key: ValueKey(label)),
            ),
          );

      await tester.pumpWidget(buildWith('one'));
      await tester.pumpAndSettle();
      expect(find.text('one'), findsOneWidget);

      await tester.pumpWidget(buildWith('two'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 120));
      // During the fade, the outgoing widget is still mounted inside the
      // FadeTransition — verify the swap renders without throwing.
      expect(find.byType(FadeTransition), findsWidgets);
      expect(find.byType(SlideTransition), findsWidgets);

      await tester.pumpAndSettle();
      expect(find.text('two'), findsOneWidget);
      expect(find.text('one'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });
}
