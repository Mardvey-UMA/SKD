import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/nav/side_nav.dart';

import '../golden_harness.dart';

/// State-matrix golden for `SideNav` — one column per active tab index.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 12.
void main() {
  setUpAll(configureGoldenEnvironment);

  testWidgets('side_nav active-index matrix golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const ColoredBox(
        color: NFColors.bg,
        child: _SideNavMatrix(),
      ),
      breakpoint: GoldenBreakpoint.desktop,
    );
    await expectGolden(
      find.byType(_SideNavMatrix),
      'side_nav_matrix.png',
    );
  });
}

class _SideNavMatrix extends StatelessWidget {
  const _SideNavMatrix();

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topLeft,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          for (int i = 0; i < 4; i++) ...<Widget>[
            SideNav(activeIndex: i, onTab: (_) {}),
            const SizedBox(width: 8),
          ],
        ],
      ),
    );
  }
}
