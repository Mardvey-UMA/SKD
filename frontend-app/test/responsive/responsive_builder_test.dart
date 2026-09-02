import 'package:flutter/widgets.dart';
import 'package:frontend_app/responsive/breakpoint.dart';
import 'package:frontend_app/responsive/responsive_builder.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Future<Breakpoint> resolveAtWidth(
    WidgetTester tester,
    double width,
  ) async {
    tester.view.physicalSize = Size(width, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    late Breakpoint reported;
    await tester.pumpWidget(
      MediaQuery.fromView(
        view: tester.view,
        child: ResponsiveBuilder(
          builder: (context, bp) {
            reported = bp;
            return const SizedBox.expand();
          },
        ),
      ),
    );
    return reported;
  }

  testWidgets('375px → Breakpoint.mobile', (tester) async {
    expect(await resolveAtWidth(tester, 375), Breakpoint.mobile);
  });

  testWidgets('900px → Breakpoint.tablet', (tester) async {
    expect(await resolveAtWidth(tester, 900), Breakpoint.tablet);
  });

  testWidgets('1440px → Breakpoint.desktop', (tester) async {
    expect(await resolveAtWidth(tester, 1440), Breakpoint.desktop);
  });

  testWidgets('boundary 767px → mobile, 768px → tablet', (tester) async {
    expect(await resolveAtWidth(tester, 767), Breakpoint.mobile);
    expect(await resolveAtWidth(tester, 768), Breakpoint.tablet);
  });

  testWidgets('boundary 1199px → tablet, 1200px → desktop', (tester) async {
    expect(await resolveAtWidth(tester, 1199), Breakpoint.tablet);
    expect(await resolveAtWidth(tester, 1200), Breakpoint.desktop);
  });
}
