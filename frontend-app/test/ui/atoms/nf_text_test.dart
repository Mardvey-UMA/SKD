import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/responsive/breakpoint.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';

void main() {
  Future<void> pump(WidgetTester tester, Widget child, {Size? size}) async {
    if (size != null) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });
    }
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: child,
      ),
    );
  }

  group('NFText.mono', () {
    testWidgets('uppercases the value and applies mono styling',
        (tester) async {
      await pump(tester, const NFText.mono('реклама · brand'));

      final Text text = tester.widget<Text>(find.byType(Text));
      expect(text.data, 'РЕКЛАМА · BRAND');

      final TextStyle style = text.style!;
      expect(style.fontSize, 10);
      expect(style.fontWeight, FontWeight.w700);
      expect(style.letterSpacing, 0.8);
      expect(style.color, NFColors.mute);
    });

    testWidgets('color override wins over default mute', (tester) async {
      await pump(
        tester,
        const NFText.mono('x', color: NFColors.ink),
      );
      final Text text = tester.widget<Text>(find.byType(Text));
      expect(text.style!.color, NFColors.ink);
    });
  });

  group('NFText.display', () {
    testWidgets('explicit breakpoint resolves size/letter-spacing',
        (tester) async {
      await pump(
        tester,
        const NFText.display('Радар', breakpoint: Breakpoint.mobile),
      );
      var style = tester.widget<Text>(find.byType(Text)).style!;
      expect(style.fontSize, 38);
      expect(style.letterSpacing, -1.2);

      await pump(
        tester,
        const NFText.display('Радар', breakpoint: Breakpoint.tablet),
      );
      style = tester.widget<Text>(find.byType(Text)).style!;
      expect(style.fontSize, 42);
      expect(style.letterSpacing, -1.4);

      await pump(
        tester,
        const NFText.display('Радар', breakpoint: Breakpoint.desktop),
      );
      style = tester.widget<Text>(find.byType(Text)).style!;
      expect(style.fontSize, 46);
      expect(style.letterSpacing, -1.6);
    });

    testWidgets('falls back to context.breakpoint when arg omitted',
        (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        const MediaQuery(
          data: MediaQueryData(size: Size(400, 800)),
          child: Directionality(
            textDirection: TextDirection.ltr,
            child: NFText.display('Радар'),
          ),
        ),
      );
      final style = tester.widget<Text>(find.byType(Text)).style!;
      expect(style.fontSize, 38);
      expect(style.letterSpacing, -1.2);
    });
  });
}
