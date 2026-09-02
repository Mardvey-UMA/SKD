import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/sheets/card_menu.dart';

void main() {
  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(400, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: MediaQuery(
          data: const MediaQueryData(size: Size(400, 900)),
          child: Stack(children: <Widget>[child]),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('renders source header, both menu rows and cancel row',
      (WidgetTester tester) async {
    await pump(
      tester,
      CardMenu(
        sourceTitle: 'Meduza',
        sourceHandle: '@meduza',
        isPremium: true,
        onClose: () {},
        onAddToSpace: () {},
        onHideSource: () {},
      ),
    );

    expect(find.text('Meduza'), findsOneWidget);
    expect(find.text('@MEDUZA'), findsOneWidget);
    expect(find.text('Добавить источник в пространство'), findsOneWidget);
    expect(find.text('Скрыть источник'), findsOneWidget);
    expect(find.text('Отмена'), findsOneWidget);
    expect(find.byType(MenuRow), findsNWidgets(2));
  });

  testWidgets('free-plan shows PREMIUM chip and locks add-to-space row',
      (WidgetTester tester) async {
    int addTaps = 0;
    await pump(
      tester,
      CardMenu(
        sourceTitle: 'Meduza',
        sourceHandle: '@meduza',
        isPremium: false,
        onClose: () {},
        onAddToSpace: () => addTaps++,
        onHideSource: () {},
      ),
    );

    expect(find.text('PREMIUM'), findsOneWidget);
    expect(find.text('Доступно в Premium'), findsOneWidget);

    // Tap on locked row — onAddToSpace must NOT fire.
    await tester.tap(find.text('Добавить источник в пространство'));
    await tester.pumpAndSettle();
    expect(addTaps, 0);
  });

  testWidgets('tap Cancel fires onClose exactly once',
      (WidgetTester tester) async {
    int closes = 0;
    await pump(
      tester,
      CardMenu(
        sourceTitle: 'Meduza',
        sourceHandle: '@meduza',
        isPremium: true,
        onClose: () => closes++,
        onAddToSpace: () {},
        onHideSource: () {},
      ),
    );

    await tester.tap(find.text('Отмена'));
    await tester.pumpAndSettle();
    expect(closes, 1);
  });

  testWidgets('tap scrim fires onClose exactly once',
      (WidgetTester tester) async {
    int closes = 0;
    await pump(
      tester,
      CardMenu(
        sourceTitle: 'Meduza',
        sourceHandle: '@meduza',
        isPremium: true,
        onClose: () => closes++,
        onAddToSpace: () {},
        onHideSource: () {},
      ),
    );

    // The scrim is the full-parent GestureDetector — tap near the top of
    // the overlay, well away from the sheet (which is bottom-anchored).
    await tester.tapAt(const Offset(200, 50));
    await tester.pumpAndSettle();
    expect(closes, 1);
  });

  testWidgets('tap add-to-space row fires onAddToSpace when premium',
      (WidgetTester tester) async {
    int adds = 0;
    await pump(
      tester,
      CardMenu(
        sourceTitle: 'Meduza',
        sourceHandle: '@meduza',
        isPremium: true,
        onClose: () {},
        onAddToSpace: () => adds++,
        onHideSource: () {},
      ),
    );

    await tester.tap(find.text('Добавить источник в пространство'));
    await tester.pumpAndSettle();
    expect(adds, 1);
  });
}
