import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';
import 'package:frontend_app/ui/nav/bottom_nav.dart';

void main() {
  const List<String> labels = <String>[
    'Лента',
    'Подборки',
    'Профиль',
    'Настройки',
  ];

  Future<void> pump(
    WidgetTester tester, {
    required int activeIndex,
    required ValueChanged<int> onTab,
    ValueChanged<int>? onRetap,
  }) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: MediaQuery(
          data: const MediaQueryData(),
          child: Center(
            child: SizedBox(
              width: 360,
              child: BottomNav(
                activeIndex: activeIndex,
                onTab: onTab,
                onRetap: onRetap,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Finder findTab(String label) => find.bySemanticsLabel(label);

  group('BottomNav', () {
    testWidgets('exposes 4 tabs via Semantics', (tester) async {
      await pump(tester, activeIndex: 0, onTab: (_) {});
      for (final String label in labels) {
        expect(findTab(label), findsOneWidget);
      }
    });

    testWidgets('activeIndex 0..3 tints the matching icon with lime',
        (tester) async {
      for (int active = 0; active < 4; active++) {
        await pump(tester, activeIndex: active, onTab: (_) {});
        await tester.pumpAndSettle();

        for (int i = 0; i < 4; i++) {
          final Finder tab = findTab(labels[i]);
          final NFIcon icon = tester.widget<NFIcon>(
            find.descendant(of: tab, matching: find.byType(NFIcon)),
          );
          if (i == active) {
            expect(icon.color, NFColors.lime,
                reason: 'active=$active, tab=$i icon should be lime');
          } else {
            expect(icon.color, NFColors.ink2,
                reason: 'active=$active, tab=$i icon should be ink2');
          }
        }
      }
    });

    testWidgets('tap on inactive tab invokes onTab with that index',
        (tester) async {
      int? tapped;
      int retapCount = 0;
      await pump(
        tester,
        activeIndex: 0,
        onTab: (int i) => tapped = i,
        onRetap: (_) => retapCount++,
      );

      await tester.tap(findTab('Подборки'));
      await tester.pumpAndSettle();

      expect(tapped, 1);
      expect(retapCount, 0);
    });

    testWidgets('tap on already-active tab invokes onRetap, not onTab',
        (tester) async {
      int tapCount = 0;
      int? retapped;
      await pump(
        tester,
        activeIndex: 2,
        onTab: (_) => tapCount++,
        onRetap: (int i) => retapped = i,
      );

      await tester.tap(findTab('Профиль'));
      await tester.pumpAndSettle();

      expect(retapped, 2);
      expect(tapCount, 0);
    });
  });
}
