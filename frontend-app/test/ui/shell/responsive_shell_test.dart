import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/responsive/breakpoint.dart';
import 'package:frontend_app/ui/nav/bottom_nav.dart';
import 'package:frontend_app/ui/nav/side_nav.dart';
import 'package:frontend_app/ui/shell/device_frame.dart';
import 'package:frontend_app/ui/shell/responsive_shell.dart';

void main() {
  const Key childKey = Key('shell-child');

  Future<void> pumpShell(
    WidgetTester tester, {
    required Size surface,
    required int activeTab,
    required ValueChanged<int> onTab,
    bool showBottomNav = true,
  }) async {
    tester.view.physicalSize = surface;
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: MediaQuery(
          data: MediaQueryData(size: surface, devicePixelRatio: 1.0),
          child: ResponsiveShell(
            bp: Breakpoints.fromWidth(surface.width),
            activeTab: activeTab,
            onTab: onTab,
            showBottomNav: showBottomNav,
            child: const SizedBox.expand(key: childKey),
          ),
        ),
      ),
    );
  }

  group('ResponsiveShell', () {
    testWidgets('mobile (375): DeviceFrame + BottomNav visible', (
      WidgetTester tester,
    ) async {
      await pumpShell(
        tester,
        surface: const Size(375, 812),
        activeTab: 0,
        onTab: (_) {},
      );

      expect(find.byType(DeviceFrame), findsOneWidget);
      expect(find.byType(BottomNav), findsOneWidget);
      expect(find.byType(SideNav), findsNothing);
      expect(find.byKey(childKey), findsOneWidget);
    });

    testWidgets('tablet (900): 520-max panel + BottomNav visible', (
      WidgetTester tester,
    ) async {
      await pumpShell(
        tester,
        surface: const Size(900, 1200),
        activeTab: 0,
        onTab: (_) {},
      );

      expect(find.byType(BottomNav), findsOneWidget);
      expect(find.byType(SideNav), findsNothing);
      expect(find.byType(DeviceFrame), findsNothing);

      // Panel width is capped at 520 by the inner ConstrainedBox.
      final ConstrainedBox panel = tester
          .widgetList<ConstrainedBox>(find.byType(ConstrainedBox))
          .firstWhere(
            (ConstrainedBox c) => c.constraints.maxWidth == 520,
          );
      expect(panel.constraints.maxWidth, 520);
    });

    testWidgets('desktop (1440): SideNav visible, no BottomNav', (
      WidgetTester tester,
    ) async {
      await pumpShell(
        tester,
        surface: const Size(1440, 900),
        activeTab: 0,
        onTab: (_) {},
      );

      expect(find.byType(SideNav), findsOneWidget);
      expect(find.byType(BottomNav), findsNothing);
      expect(find.byType(DeviceFrame), findsNothing);
      expect(find.byKey(childKey), findsOneWidget);
    });

    testWidgets('showBottomNav=false hides BottomNav on mobile', (
      WidgetTester tester,
    ) async {
      await pumpShell(
        tester,
        surface: const Size(375, 812),
        activeTab: 0,
        onTab: (_) {},
        showBottomNav: false,
      );

      expect(find.byType(DeviceFrame), findsOneWidget);
      expect(find.byType(BottomNav), findsNothing);
    });

    testWidgets('tab switch via onTab propagates to activeTab on rebuild', (
      WidgetTester tester,
    ) async {
      int active = 0;
      late StateSetter setActive;

      tester.view.physicalSize = const Size(375, 812);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: MediaQuery(
            data: const MediaQueryData(
              size: Size(375, 812),
              devicePixelRatio: 1.0,
            ),
            child: StatefulBuilder(
              builder: (BuildContext ctx, StateSetter setState) {
                setActive = setState;
                return ResponsiveShell(
                  bp: Breakpoint.mobile,
                  activeTab: active,
                  onTab: (int i) => setState(() => active = i),
                  child: const SizedBox.expand(key: childKey),
                );
              },
            ),
          ),
        ),
      );

      // Simulate onTab firing from outside (BottomNav tap path).
      setActive(() => active = 2);
      await tester.pump();

      final BottomNav nav = tester.widget<BottomNav>(find.byType(BottomNav));
      expect(nav.activeIndex, 2);
    });
  });
}
