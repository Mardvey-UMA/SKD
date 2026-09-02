import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/multi_image.dart';
import 'package:frontend_app/ui/atoms/single_image.dart';
import 'package:frontend_app/ui/atoms/stripe_placeholder.dart';
import 'package:frontend_app/ui/cards/card_item.dart';
import 'package:frontend_app/ui/cards/long_card.dart';

CardItem _item(CardImages images) {
  return CardItem(
    id: 'long-1',
    source: 'VC.RU',
    time: DateTime.now().subtract(const Duration(hours: 2)),
    readTime: '8 мин чтения',
    title: 'Как архитекторы пересобирают фронтенд-стек в 2026 году',
    snippet:
        'Постепенный отказ от REST в пользу GraphQL и typed RPC, серверные компоненты, новые подходы к бандлингу и гидратации — разбираемся, что меняется и почему это важно для продуктовых команд. Полный разбор с примерами кода, метриками производительности и выводами нескольких консалтинговых студий.',
    images: images,
    tone: StripeTone.ink,
    toneSecondary: StripeTone.accent,
    toneTertiary: StripeTone.lime,
    seed: 7,
  );
}

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

bool _hasBottomFadeGradient(WidgetTester tester) {
  final Iterable<DecoratedBox> boxes =
      tester.widgetList<DecoratedBox>(find.byType(DecoratedBox));
  for (final DecoratedBox box in boxes) {
    final Decoration d = box.decoration;
    if (d is BoxDecoration) {
      final Gradient? g = d.gradient;
      if (g is LinearGradient &&
          g.begin == Alignment.topCenter &&
          g.end == Alignment.bottomCenter &&
          g.colors.length == 2 &&
          g.colors.first.a == 0.0) {
        return true;
      }
    }
  }
  return false;
}

void main() {
  group('LongCard', () {
    testWidgets('renders images=one variant without exception', (tester) async {
      await _pump(
        tester,
        LongCard(
          item: _item(CardImages.one),
          onOpen: (_) {},
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(SingleImage), findsOneWidget);
      expect(find.byType(MultiImage), findsNothing);
    });

    testWidgets('renders images=multi variant without exception',
        (tester) async {
      await _pump(
        tester,
        LongCard(
          item: _item(CardImages.multi),
          onOpen: (_) {},
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(MultiImage), findsOneWidget);
      expect(find.byType(SingleImage), findsNothing);
    });

    testWidgets('renders images=none variant without image block',
        (tester) async {
      await _pump(
        tester,
        LongCard(
          item: _item(CardImages.none),
          onOpen: (_) {},
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(SingleImage), findsNothing);
      expect(find.byType(MultiImage), findsNothing);
    });

    testWidgets('renders fade overlay gradient over the clamped snippet',
        (tester) async {
      await _pump(
        tester,
        LongCard(
          item: _item(CardImages.one),
          onOpen: (_) {},
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );
      expect(_hasBottomFadeGradient(tester), isTrue);
    });

    testWidgets('tap on title calls onOpen exactly once', (tester) async {
      int opens = 0;
      CardItem? received;
      final CardItem item = _item(CardImages.one);
      await _pump(
        tester,
        LongCard(
          item: item,
          onOpen: (CardItem i) {
            opens++;
            received = i;
          },
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );

      await tester.tap(find.text(item.title));
      await tester.pumpAndSettle();

      expect(opens, 1);
      expect(received?.id, item.id);
    });

    testWidgets('tap on CTA "Читать далее" calls onOpen exactly once',
        (tester) async {
      int opens = 0;
      await _pump(
        tester,
        LongCard(
          item: _item(CardImages.one),
          onOpen: (_) => opens++,
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );

      await tester.tap(find.text('Читать далее'));
      await tester.pumpAndSettle();

      expect(opens, 1);
    });

    testWidgets('tap on reaction does NOT call onOpen', (tester) async {
      int opens = 0;
      int reactCalls = 0;
      ReactionKind? lastKind;
      await _pump(
        tester,
        LongCard(
          item: _item(CardImages.one),
          onOpen: (_) => opens++,
          onReact: (CardItem _, ReactionKind kind) {
            reactCalls++;
            lastKind = kind;
          },
        ),
      );

      await tester.tap(find.bySemanticsLabel('В закладки'));
      await tester.pumpAndSettle();

      expect(reactCalls, 1);
      expect(lastKind, ReactionKind.bookmark);
      expect(opens, 0);
    });
  });
}
