import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/feed_skeleton.dart';
import 'package:visibility_detector/visibility_detector.dart';

void main() {
  setUpAll(() {
    // Disable the detector's default 500 ms update delay so the first
    // visibility callback lands inside the first pump window of the tests.
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  Future<void> pump(WidgetTester tester, Widget child) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: MediaQuery(
          data: const MediaQueryData(size: Size(390, 2400)),
          child: SingleChildScrollView(child: child),
        ),
      ),
    );
  }

  testWidgets('renders 3 placeholder cards', (WidgetTester tester) async {
    await pump(tester, const FeedSkeleton());
    await tester.pump();

    // Three outer skeleton cards are the three DecoratedBox children with
    // `NFRadii.brLg` + hairline border. We use a lighter-weight assertion via
    // the private static key: count cards by finding the shared shimmer
    // ShaderMask and walking its children.
    final Finder shaderMask = find.byType(ShaderMask);
    expect(shaderMask, findsOneWidget);

    // Each _SkeletonCard is a DecoratedBox + Padding(all: 8) containing a
    // Column with a 180-tall "image" block + text column. Cards contain the
    // 36×36 pill row (3 circles), so count those pill-row SizedBoxes.
    // There are exactly 3×3 = 9 circular 36×36 placeholders in the shimmer.
    final Finder pills = find.byWidgetPredicate((Widget w) {
      return w is SizedBox && w.height == 36 && w.width == 36;
    });
    expect(pills, findsNWidgets(9));
  });

  testWidgets('renders exactly 3 skeleton image blocks of height 180',
      (WidgetTester tester) async {
    await pump(tester, const FeedSkeleton());
    await tester.pump();

    final Finder imageBlocks = find.byWidgetPredicate((Widget w) {
      return w is SizedBox && w.height == 180 && w.width == null;
    });
    expect(imageBlocks, findsNWidgets(3));
  });

  testWidgets(
    'TickerMode is muted when parent disables tickers',
    (WidgetTester tester) async {
      await pump(
        tester,
        const TickerMode(
          enabled: false,
          child: FeedSkeleton(),
        ),
      );
      await tester.pump();

      // Walk down to any descendant of the FeedSkeleton's internal
      // TickerMode — an ancestor-disabled TickerMode must propagate
      // `muted == true` to the subtree.
      final BuildContext innerCtx = tester.element(find.byType(ShaderMask));
      expect(TickerMode.valuesOf(innerCtx).enabled, isFalse);
    },
  );

  testWidgets('renders header mono caption', (WidgetTester tester) async {
    await pump(tester, const FeedSkeleton());
    await tester.pump();
    expect(find.text('ВАША ЛЕНТА ФОРМИРУЕТСЯ...'), findsOneWidget);
    expect(
      find.text('Подбираем свежие материалы по вашим интересам'),
      findsOneWidget,
    );
  });
}
