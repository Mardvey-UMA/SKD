import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/nav/side_nav.dart';

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
  }) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: MediaQuery(
          data: const MediaQueryData(size: Size(1440, 900)),
          child: Align(
            alignment: Alignment.topLeft,
            child: SideNav(
              activeIndex: activeIndex,
              onTab: onTab,
            ),
          ),
        ),
      ),
    );
  }

  group('SideNav', () {
    testWidgets('renders header + 4 tabs at width 240', (tester) async {
      await pump(tester, activeIndex: 0, onTab: (_) {});

      // Header wordmark.
      expect(find.text('Радар'), findsOneWidget);

      // 4 tab labels.
      for (final String label in labels) {
        expect(find.text(label), findsOneWidget);
      }

      // Root container is 240 wide.
      final Finder rootFinder = find.byType(Container).first;
      final RenderBox box = tester.renderObject<RenderBox>(rootFinder);
      expect(box.size.width, 240);
    });

    testWidgets('active tab paints NFColors.ink background', (tester) async {
      for (int active = 0; active < 4; active++) {
        await pump(tester, activeIndex: active, onTab: (_) {});
        await tester.pumpAndSettle();

        final Finder containerFinder = find.ancestor(
          of: find.text(labels[active]),
          matching: find.byType(AnimatedContainer),
        );
        final AnimatedContainer container =
            tester.widget<AnimatedContainer>(containerFinder);
        final BoxDecoration decoration =
            container.decoration! as BoxDecoration;
        expect(
          decoration.color,
          NFColors.ink,
          reason: 'active tab $active should have ink background',
        );

        // Sibling inactive tabs must have transparent fill.
        for (int i = 0; i < 4; i++) {
          if (i == active) continue;
          final Finder inactive = find.ancestor(
            of: find.text(labels[i]),
            matching: find.byType(AnimatedContainer),
          );
          final AnimatedContainer c =
              tester.widget<AnimatedContainer>(inactive);
          final BoxDecoration d = c.decoration! as BoxDecoration;
          expect(d.color, const Color(0x00000000),
              reason: 'inactive tab $i (active=$active) should be transparent');
        }
      }
    });

    testWidgets('tap on inactive tab invokes onTab with that index',
        (tester) async {
      int? tapped;
      await pump(
        tester,
        activeIndex: 0,
        onTab: (int i) => tapped = i,
      );

      await tester.tap(find.text('Профиль'));
      await tester.pumpAndSettle();

      expect(tapped, 2);
    });
  });
}
