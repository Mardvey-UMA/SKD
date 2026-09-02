import 'package:flutter/widgets.dart';
import 'package:frontend_app/responsive/breakpoint.dart';
import 'package:frontend_app/responsive/responsive_value.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ResponsiveValue.resolveFor', () {
    test('desktop falls back to tablet, then to mobile', () {
      const v = ResponsiveValue<int>(mobile: 10, desktop: 20);
      expect(v.resolveFor(Breakpoint.mobile), 10);
      expect(v.resolveFor(Breakpoint.tablet), 10);
      expect(v.resolveFor(Breakpoint.desktop), 20);
    });

    test('tablet falls back to mobile when not provided', () {
      const v = ResponsiveValue<String>(mobile: 'm');
      expect(v.resolveFor(Breakpoint.mobile), 'm');
      expect(v.resolveFor(Breakpoint.tablet), 'm');
      expect(v.resolveFor(Breakpoint.desktop), 'm');
    });

    test('all three values are distinct when all provided', () {
      const v = ResponsiveValue<int>(mobile: 1, tablet: 2, desktop: 3);
      expect(v.resolveFor(Breakpoint.mobile), 1);
      expect(v.resolveFor(Breakpoint.tablet), 2);
      expect(v.resolveFor(Breakpoint.desktop), 3);
    });
  });

  group('ResponsiveValue.resolve via BuildContext', () {
    Future<void> pumpWidth(WidgetTester tester, double width) async {
      tester.view.physicalSize = Size(width, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
    }

    testWidgets('375 → mobile → 10', (tester) async {
      await pumpWidth(tester, 375);
      int? resolved;
      await tester.pumpWidget(
        MediaQuery.fromView(
          view: tester.view,
          child: Builder(
            builder: (context) {
              const v = ResponsiveValue<int>(mobile: 10, desktop: 20);
              resolved = v.resolve(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      expect(resolved, 10);
    });

    testWidgets('900 → tablet → 10 (fallback to mobile)', (tester) async {
      await pumpWidth(tester, 900);
      int? resolved;
      await tester.pumpWidget(
        MediaQuery.fromView(
          view: tester.view,
          child: Builder(
            builder: (context) {
              const v = ResponsiveValue<int>(mobile: 10, desktop: 20);
              resolved = v.resolve(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      expect(resolved, 10);
    });

    testWidgets('1440 → desktop → 20', (tester) async {
      await pumpWidth(tester, 1440);
      int? resolved;
      await tester.pumpWidget(
        MediaQuery.fromView(
          view: tester.view,
          child: Builder(
            builder: (context) {
              const v = ResponsiveValue<int>(mobile: 10, desktop: 20);
              resolved = v.resolve(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      expect(resolved, 20);
    });
  });
}
