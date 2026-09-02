import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/models/ad.dart';
import 'package:frontend_app/ui/cards/ad_card.dart';

const Ad _ad = Ad(
  brand: 'Yandex',
  tagline: 'Быстрая оплата в один тап',
  title: 'Оплачивайте подписки в одно нажатие',
  desc:
      'Yandex Pay помогает расплачиваться в интернете без ввода данных карты '
      'каждый раз.',
  cta: 'Попробовать →',
);

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(375, 1200);
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
        child: SizedBox(width: 375, child: child),
      ),
    ),
  );
}

void main() {
  group('AdStyle', () {
    test('enum values match the spec set', () {
      expect(AdStyle.values, <AdStyle>[
        AdStyle.off,
        AdStyle.subtle,
        AdStyle.card,
        AdStyle.banner,
      ]);
    });
  });

  group('AdCard rendering', () {
    testWidgets('renders subtle without exception', (tester) async {
      await _pump(
        tester,
        AdCard(ad: _ad, onClick: (_) {}, onHide: (_) {}),
      );
      expect(tester.takeException(), isNull);
      expect(find.text(_ad.title), findsOneWidget);
      expect(find.text(_ad.tagline), findsOneWidget);
    });

    testWidgets('renders card without exception', (tester) async {
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          style: AdStyle.card,
          onClick: (_) {},
          onHide: (_) {},
        ),
      );
      expect(tester.takeException(), isNull);
      // Card style shows desc (not tagline) in body.
      expect(find.text(_ad.desc), findsOneWidget);
      // Giant brand wordmark on hatched header + label «РЕКЛАМА · BRAND».
      expect(find.text(_ad.brand), findsOneWidget);
    });

    testWidgets('renders banner without exception', (tester) async {
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          style: AdStyle.banner,
          onClick: (_) {},
          onHide: (_) {},
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.text(_ad.title), findsOneWidget);
      expect(find.text(_ad.tagline), findsOneWidget);
      // First letter of brand in the 54×54 lime tile.
      expect(find.text('Y'), findsOneWidget);
    });

    testWidgets('AdStyle.off renders nothing visible', (tester) async {
      await _pump(
        tester,
        const AdCard(ad: _ad, style: AdStyle.off),
      );
      expect(tester.takeException(), isNull);
      expect(find.text(_ad.title), findsNothing);
      expect(find.text(_ad.tagline), findsNothing);
    });
  });

  group('AdCard callbacks', () {
    testWidgets('tap close fires onHide and NOT onClick (subtle)',
        (tester) async {
      int clicks = 0;
      int hides = 0;
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          onClick: (_) => clicks++,
          onHide: (_) => hides++,
        ),
      );

      await tester.tap(find.bySemanticsLabel('Скрыть рекламу'));
      await tester.pumpAndSettle();

      expect(hides, 1);
      expect(clicks, 0);
    });

    testWidgets('tap close fires onHide and NOT onClick (card)',
        (tester) async {
      int clicks = 0;
      int hides = 0;
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          style: AdStyle.card,
          onClick: (_) => clicks++,
          onHide: (_) => hides++,
        ),
      );

      await tester.tap(find.bySemanticsLabel('Скрыть рекламу'));
      await tester.pumpAndSettle();

      expect(hides, 1);
      expect(clicks, 0);
    });

    testWidgets('tap close fires onHide and NOT onClick (banner)',
        (tester) async {
      int clicks = 0;
      int hides = 0;
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          style: AdStyle.banner,
          onClick: (_) => clicks++,
          onHide: (_) => hides++,
        ),
      );

      await tester.tap(find.bySemanticsLabel('Скрыть рекламу'));
      await tester.pumpAndSettle();

      expect(hides, 1);
      expect(clicks, 0);
    });

    testWidgets('tap on body fires onClick (subtle)', (tester) async {
      int clicks = 0;
      Ad? received;
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          onClick: (Ad a) {
            clicks++;
            received = a;
          },
          onHide: (_) {},
        ),
      );

      await tester.tap(find.text(_ad.title));
      await tester.pumpAndSettle();

      expect(clicks, 1);
      expect(received?.brand, _ad.brand);
    });

    testWidgets('tap on body fires onClick (card)', (tester) async {
      int clicks = 0;
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          style: AdStyle.card,
          onClick: (_) => clicks++,
          onHide: (_) {},
        ),
      );

      await tester.tap(find.text(_ad.title));
      await tester.pumpAndSettle();

      expect(clicks, 1);
    });

    testWidgets('tap on body fires onClick (banner)', (tester) async {
      int clicks = 0;
      await _pump(
        tester,
        AdCard(
          ad: _ad,
          style: AdStyle.banner,
          onClick: (_) => clicks++,
          onHide: (_) {},
        ),
      );

      await tester.tap(find.text(_ad.title));
      await tester.pumpAndSettle();

      expect(clicks, 1);
    });
  });
}
