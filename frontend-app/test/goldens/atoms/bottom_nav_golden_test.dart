import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/nav/bottom_nav.dart';

import '../golden_harness.dart';

/// State-matrix golden for `BottomNav` — one row per active tab index.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 11.
void main() {
  setUpAll(configureGoldenEnvironment);

  testWidgets('bottom_nav active-index matrix golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const ColoredBox(
        color: NFColors.bg,
        child: _BottomNavMatrix(),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(_BottomNavMatrix),
      'bottom_nav_matrix.png',
    );
  });
}

class _BottomNavMatrix extends StatelessWidget {
  const _BottomNavMatrix();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          for (int i = 0; i < 4; i++) ...<Widget>[
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  'active $i',
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                    color: NFColors.mute,
                  ),
                ),
              ),
            ),
            BottomNav(activeIndex: i, onTab: (_) {}),
            const SizedBox(height: 14),
          ],
        ],
      ),
    );
  }
}
