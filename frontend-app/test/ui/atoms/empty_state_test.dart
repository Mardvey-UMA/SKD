import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/empty_state.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';

void main() {
  Future<void> pump(WidgetTester tester, Widget child) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: MediaQuery(
          data: const MediaQueryData(size: Size(390, 900)),
          child: Align(
            alignment: Alignment.topCenter,
            child: SizedBox(width: 360, child: child),
          ),
        ),
      ),
    );
  }

  testWidgets('renders with icon + title + desc + action',
      (WidgetTester tester) async {
    await pump(
      tester,
      EmptyState(
        iconName: 'bookmark',
        title: 'Пока ничего нет',
        desc: 'Здесь появятся ваши сохранённые материалы',
        action: const SizedBox(
          key: Key('primary-action'),
          width: 120,
          height: 40,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Пока ничего нет'), findsOneWidget);
    expect(
      find.text('Здесь появятся ваши сохранённые материалы'),
      findsOneWidget,
    );
    expect(find.byType(NFIcon), findsOneWidget);
    expect(find.byKey(const Key('primary-action')), findsOneWidget);

    final NFIcon icon = tester.widget<NFIcon>(find.byType(NFIcon));
    expect(icon.name, 'bookmark');
    expect(icon.size, 22);
  });

  testWidgets('renders without desc + action', (WidgetTester tester) async {
    await pump(
      tester,
      const EmptyState(iconName: 'bookmark', title: 'Пусто'),
    );
    await tester.pump();

    expect(find.text('Пусто'), findsOneWidget);
    expect(find.byType(NFIcon), findsOneWidget);
  });

  testWidgets('accent defaults to lime', (WidgetTester tester) async {
    await pump(
      tester,
      const EmptyState(title: 'T'),
    );
    await tester.pump();

    final EmptyState state = tester.widget<EmptyState>(find.byType(EmptyState));
    expect(state.accent, EmptyStateAccent.lime);
  });
}
