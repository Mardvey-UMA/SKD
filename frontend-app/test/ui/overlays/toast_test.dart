import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/overlays/toast.dart';

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
  }

  testWidgets('renders text body', (WidgetTester tester) async {
    await pump(
      tester,
      Toast(
        text: 'Источник «Meduza» скрыт',
        duration: const Duration(milliseconds: 2600),
        onDismiss: () {},
      ),
    );
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Источник «Meduza» скрыт'), findsOneWidget);
    // No undo button in this variant.
    expect(find.text('Вернуть'), findsNothing);
  });

  testWidgets('renders optional Вернуть button and fires onUndo',
      (WidgetTester tester) async {
    int undos = 0;
    int dismisses = 0;
    await pump(
      tester,
      Toast(
        text: 'Источник скрыт',
        duration: const Duration(milliseconds: 3400),
        onDismiss: () => dismisses++,
        undoLabel: 'Вернуть',
        onUndo: () => undos++,
      ),
    );
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Вернуть'), findsOneWidget);

    await tester.tap(find.text('Вернуть'));
    await tester.pump();

    expect(undos, 1);
    expect(dismisses, 1);

    // Drain the pending auto-dismiss timer so the test engine is clean.
    await tester.pump(const Duration(milliseconds: 3500));
  });

  testWidgets('auto-dismisses after specified duration',
      (WidgetTester tester) async {
    int dismisses = 0;
    await pump(
      tester,
      Toast(
        text: 'Добавлено в «Tech»',
        duration: const Duration(milliseconds: 2600),
        onDismiss: () => dismisses++,
      ),
    );
    await tester.pump(const Duration(milliseconds: 50));
    expect(dismisses, 0);

    // Advance just past the 2600ms window.
    await tester.pump(const Duration(milliseconds: 2700));
    expect(dismisses, 1);
  });

  testWidgets('passes different durations to caller',
      (WidgetTester tester) async {
    int dismisses = 0;
    await pump(
      tester,
      Toast(
        text: 'With undo',
        duration: const Duration(milliseconds: 3400),
        onDismiss: () => dismisses++,
      ),
    );
    await tester.pump(const Duration(milliseconds: 2700));
    expect(dismisses, 0);

    await tester.pump(const Duration(milliseconds: 800));
    expect(dismisses, 1);
  });
}
