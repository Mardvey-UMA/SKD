import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/multi_image.dart';
import 'package:frontend_app/ui/atoms/single_image.dart';
import 'package:frontend_app/ui/atoms/stripe_placeholder.dart';
import 'package:frontend_app/ui/cards/card_item.dart';
import 'package:frontend_app/ui/cards/short_card.dart';

CardItem _item(CardImages images) {
  return CardItem(
    id: 'id-1',
    source: 'Хабр',
    time: DateTime.now().subtract(const Duration(minutes: 5)),
    readTime: '2 мин чтения',
    title: 'Нейросети научили распознавать сарказм в комментариях',
    snippet:
        'Исследователи из MIT представили новую архитектуру, которая анализирует контекст сообщений и определяет эмоциональный окрас текста.',
    images: images,
    tone: StripeTone.accent,
    toneSecondary: StripeTone.lime,
    toneTertiary: StripeTone.warn,
    seed: 42,
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

void main() {
  group('ShortCard', () {
    testWidgets('renders images=one variant without exception', (tester) async {
      await _pump(
        tester,
        ShortCard(
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
        ShortCard(
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
        ShortCard(
          item: _item(CardImages.none),
          onOpen: (_) {},
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(SingleImage), findsNothing);
      expect(find.byType(MultiImage), findsNothing);
    });

    testWidgets('tap on title calls onOpen exactly once', (tester) async {
      int opens = 0;
      CardItem? received;
      final CardItem item = _item(CardImages.one);
      await _pump(
        tester,
        ShortCard(
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

    testWidgets('tap on CTA "Читать" calls onOpen exactly once',
        (tester) async {
      int opens = 0;
      await _pump(
        tester,
        ShortCard(
          item: _item(CardImages.one),
          onOpen: (_) => opens++,
          onReact: (CardItem _, ReactionKind _) {},
        ),
      );

      await tester.tap(find.text('Читать'));
      await tester.pumpAndSettle();

      expect(opens, 1);
    });

    testWidgets('tap on reaction does NOT call onOpen', (tester) async {
      int opens = 0;
      int reactCalls = 0;
      ReactionKind? lastKind;
      await _pump(
        tester,
        ShortCard(
          item: _item(CardImages.one),
          onOpen: (_) => opens++,
          onReact: (CardItem _, ReactionKind kind) {
            reactCalls++;
            lastKind = kind;
          },
        ),
      );

      await tester.tap(find.bySemanticsLabel('Нравится'));
      await tester.pumpAndSettle();

      expect(reactCalls, 1);
      expect(lastKind, ReactionKind.like);
      expect(opens, 0);
    });
  });
}
