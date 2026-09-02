import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/cards/short_card.dart';

import '../../fixtures/mockup_seed.dart';
import '../golden_harness.dart';

/// State-matrix golden for `ShortCard` — renders one card per image mode
/// (`one` / `multi` / `none`) plus the three reaction-flag permutations
/// most likely to surface regressions (neutral, liked, bookmarked).
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 9.
void main() {
  setUpAll(configureGoldenEnvironment);

  final MockFeedEntry one = kMockFeed[0]; // a1 — short / one
  final MockFeedEntry multi = kMockFeed[2]; // a3 — short / multi
  final MockFeedEntry none = kMockFeed[3]; // a4 — short / none

  testWidgets('short_card state matrix golden', (WidgetTester tester) async {
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
                _labelled('images=one · neutral', ShortCard(
                  item: mockEntryToCardItem(one),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                )),
                _labelled('images=multi · liked', ShortCard(
                  item: mockEntryToCardItem(multi),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                  isLiked: true,
                )),
                _labelled('images=none · bookmarked', ShortCard(
                  item: mockEntryToCardItem(none),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                  isBookmarked: true,
                )),
              ],
            ),
          ),
        ),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(SingleChildScrollView),
      'short_card_matrix.png',
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
