import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/sheets/card_menu.dart';

import '../golden_harness.dart';

/// State-matrix golden for `CardMenu` — renders the sheet in both the
/// `isPremium = true` (add-to-space row enabled) and `isPremium = false`
/// (locked + PREMIUM chip) variants. Animation is pumped past the
/// 260 ms slide duration so the sheet sits at its resting offset.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 9 / 12.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final bool premium in <bool>[true, false]) {
    final String label = premium ? 'premium' : 'free';
    testWidgets('card_menu $label golden', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        Stack(
          children: <Widget>[
            const Positioned.fill(child: ColoredBox(color: NFColors.bg)),
            CardMenu(
              sourceTitle: 'VC.RU',
              sourceHandle: '@vcru',
              isPremium: premium,
              onClose: () {},
              onAddToSpace: () {},
              onHideSource: () {},
            ),
          ],
        ),
        breakpoint: GoldenBreakpoint.mobile,
      );
      // Slide animation is 260 ms — settle past it.
      await tester.pump(const Duration(milliseconds: 400));
      await expectGolden(
        find.byType(CardMenu),
        'card_menu_$label.png',
      );
    });
  }
}
