import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/cards/long_card.dart';

import '../../fixtures/mockup_seed.dart';
import '../golden_harness.dart';

/// State-matrix golden for `LongCard` — renders the `images=one` and
/// `images=none` long entries from the mockup seed, each with a different
/// reaction state.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 10.
void main() {
  setUpAll(configureGoldenEnvironment);

  final MockFeedEntry withImage = kMockFeed[1]; // a2 — long / one
  final MockFeedEntry noImage = kMockFeed[5]; // a6 — long / none

  testWidgets('long_card state matrix golden', (WidgetTester tester) async {
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
                _labelled('images=one · disliked', LongCard(
                  item: mockEntryToCardItem(withImage),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                  isDisliked: true,
                )),
                _labelled('images=none · liked + bookmarked', LongCard(
                  item: mockEntryToCardItem(noImage),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                  isLiked: true,
                  isBookmarked: true,
                )),
              ],
            ),
          ),
        ),
      ),
      breakpoint: GoldenBreakpoint.tablet,
    );
    await expectGolden(
      find.byType(SingleChildScrollView),
      'long_card_matrix.png',
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
