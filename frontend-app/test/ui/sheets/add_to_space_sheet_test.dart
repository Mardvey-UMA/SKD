import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/sheets/add_to_space_sheet.dart';

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

  const List<UserCollection> mockCollections = <UserCollection>[
    UserCollection(id: 'c1', title: 'Tech', tone: CollectionTone.lime),
    UserCollection(id: 'c2', title: 'News', tone: CollectionTone.accent),
    UserCollection(id: 'c3', title: 'Design', tone: CollectionTone.violet),
  ];

  testWidgets('renders create-tile + list of 3 mock collections',
      (WidgetTester tester) async {
    await pump(
      tester,
      AddToSpaceSheet(
        sourceTitle: 'Meduza',
        collections: mockCollections,
        onClose: () {},
        onCreate: () {},
        onSelect: (_) {},
      ),
    );

    expect(find.text('ДОБАВИТЬ В ПРОСТРАНСТВО'), findsOneWidget);
    expect(find.text('Создать новое пространство'), findsOneWidget);
    expect(find.text('Tech'), findsOneWidget);
    expect(find.text('News'), findsOneWidget);
    expect(find.text('Design'), findsOneWidget);
    expect(find.text('ВАШИ ПРОСТРАНСТВА · 3'), findsOneWidget);
  });

  testWidgets('tap create-tile fires onCreate exactly once',
      (WidgetTester tester) async {
    int creates = 0;
    await pump(
      tester,
      AddToSpaceSheet(
        sourceTitle: 'Meduza',
        collections: mockCollections,
        onClose: () {},
        onCreate: () => creates++,
        onSelect: (_) {},
      ),
    );

    await tester.tap(find.text('Создать новое пространство'));
    await tester.pumpAndSettle();
    expect(creates, 1);
  });

  testWidgets('tap collection-row fires onSelect(collectionId)',
      (WidgetTester tester) async {
    final List<String> selected = <String>[];
    await pump(
      tester,
      AddToSpaceSheet(
        sourceTitle: 'Meduza',
        collections: mockCollections,
        onClose: () {},
        onCreate: () {},
        onSelect: selected.add,
      ),
    );

    await tester.tap(find.text('News'));
    await tester.pumpAndSettle();
    expect(selected, <String>['c2']);

    await tester.tap(find.text('Design'));
    await tester.pumpAndSettle();
    expect(selected, <String>['c2', 'c3']);
  });

  testWidgets('tap scrim fires onClose exactly once',
      (WidgetTester tester) async {
    int closes = 0;
    await pump(
      tester,
      AddToSpaceSheet(
        sourceTitle: 'Meduza',
        collections: mockCollections,
        onClose: () => closes++,
        onCreate: () {},
        onSelect: (_) {},
      ),
    );

    await tester.tapAt(const Offset(200, 30));
    await tester.pumpAndSettle();
    expect(closes, 1);
  });

  testWidgets('empty-collections state renders placeholder',
      (WidgetTester tester) async {
    await pump(
      tester,
      AddToSpaceSheet(
        sourceTitle: 'Meduza',
        collections: const <UserCollection>[],
        onClose: () {},
        onCreate: () {},
        onSelect: (_) {},
      ),
    );

    expect(find.text('ВАШИ ПРОСТРАНСТВА · 0'), findsOneWidget);
    expect(
      find.text('У вас пока нет пространств. Создайте новое выше.'),
      findsOneWidget,
    );
  });
}
