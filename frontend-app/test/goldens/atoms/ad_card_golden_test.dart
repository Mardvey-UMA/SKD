import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/models/ad.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/cards/ad_card.dart';

import '../../fixtures/mockup_seed.dart';
import '../golden_harness.dart';

/// State-matrix golden for `AdCard` — renders the three visible styles
/// (`subtle`, `card`, `banner`) with the three ads from the mockup seed.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 15.
void main() {
  setUpAll(configureGoldenEnvironment);

  testWidgets('ad_card style matrix golden', (WidgetTester tester) async {
    final Ad skillbox = kMockAds[0].toAd();
    final Ad tBank = kMockAds[1].toAd();
    final Ad notion = kMockAds[2].toAd();

    await pumpGolden(
      tester,
      ColoredBox(
        color: NFColors.bg,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 20),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                _labelled('subtle', AdCard(ad: skillbox, style: AdStyle.subtle)),
                _labelled('card', AdCard(ad: tBank, style: AdStyle.card)),
                _labelled('banner', AdCard(ad: notion, style: AdStyle.banner)),
              ],
            ),
          ),
        ),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(SingleChildScrollView),
      'ad_card_matrix.png',
    );
  });
}

Widget _labelled(String label, Widget child) {
  return Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: <Widget>[
      Padding(
        padding: const EdgeInsets.only(bottom: 6, top: 4),
        child: Text(
          label,
          style: const TextStyle(
            fontFamily: 'Nunito',
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            color: NFColors.mute,
          ),
        ),
      ),
      child,
      const SizedBox(height: 16),
    ],
  );
}
