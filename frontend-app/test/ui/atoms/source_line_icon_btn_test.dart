import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/icon_btn.dart';
import 'package:frontend_app/ui/atoms/source_line.dart';

void main() {
  Future<void> pump(
    WidgetTester tester,
    Widget child, {
    double width = 400,
  }) async {
    tester.view.physicalSize = Size(width, 200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(width: width, child: child),
        ),
      ),
    );
  }

  group('SourceLine', () {
    testWidgets('renders source name, formatted time, and optional readTime',
        (tester) async {
      await pump(
        tester,
        SourceLine(
          source: 'Хабр',
          time: DateTime.now().subtract(const Duration(minutes: 12)),
          readTime: '2 мин чтения',
        ),
      );

      expect(find.text('Хабр'), findsOneWidget);
      // mono uppercases — "12м" → "12М".
      expect(find.text('12М'), findsOneWidget);
      expect(find.text('2 МИН ЧТЕНИЯ'), findsOneWidget);
    });

    testWidgets('wraps when source name is long (no overflow)',
        (tester) async {
      await pump(
        tester,
        SourceLine(
          source:
              'Очень длинное название источника которое должно переноситься',
          time: DateTime.now().subtract(const Duration(hours: 3)),
        ),
        width: 240,
      );

      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.byType(SourceLine), findsOneWidget);
      // Wrap is used for flowing layout.
      expect(find.byType(Wrap), findsOneWidget);
    });

    testWidgets('more button renders only when onMore is provided',
        (tester) async {
      await pump(
        tester,
        SourceLine(
          source: 'VC.RU',
          time: DateTime.now().subtract(const Duration(hours: 1)),
        ),
      );
      expect(find.bySemanticsLabel('Ещё'), findsNothing);

      int taps = 0;
      await pump(
        tester,
        SourceLine(
          source: 'VC.RU',
          time: DateTime.now().subtract(const Duration(hours: 1)),
          onMore: () => taps++,
        ),
      );
      expect(find.bySemanticsLabel('Ещё'), findsOneWidget);

      await tester.tap(find.bySemanticsLabel('Ещё'));
      expect(taps, 1);
    });
  });

  group('IconBtn', () {
    testWidgets('renders icon without warn dot by default', (tester) async {
      await pump(
        tester,
        IconBtn(
          iconName: 'bell',
          onTap: () {},
        ),
      );

      expect(find.byType(IconBtn), findsOneWidget);
      // One Container = the 38x38 surface circle. No warn dot present.
      expect(find.byType(Container), findsOneWidget);
    });

    testWidgets('renders warn dot when warnDot=true', (tester) async {
      await pump(
        tester,
        IconBtn(
          iconName: 'bell',
          warnDot: true,
          onTap: () {},
        ),
      );

      // Two Containers: the outer circle + the 7x7 warn dot.
      expect(find.byType(Container), findsNWidgets(2));
    });

    testWidgets('onTap fires exactly once per tap', (tester) async {
      int taps = 0;
      await pump(
        tester,
        IconBtn(
          iconName: 'bell',
          onTap: () => taps++,
        ),
      );

      await tester.tap(find.byType(IconBtn));
      await tester.pumpAndSettle();

      expect(taps, 1);
    });
  });
}
