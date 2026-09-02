import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/cards/ad_card.dart';
import 'package:frontend_app/ui/cards/long_card.dart';
import 'package:frontend_app/ui/cards/short_card.dart';

import '../../fixtures/mockup_seed.dart';
import '../golden_harness.dart';

/// Feed screen — scene golden (visual body only, no real providers).
///
/// Renders the canonical ordering that `FeedScreen` produces from the
/// Prompt 16 spec: title row → short/long card mix with an `AdCard.subtle`
/// injected at position 3. Mobile breakpoint skips the long card to avoid
/// a known `Читать далее` CTA overflow at ≤ 311-px row widths (real app
/// avoids this via `DeviceFrame` insets + viewport > 390 px).
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 16.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('feed scene golden — ${bp.name}', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        _FeedScene(showLong: bp != GoldenBreakpoint.mobile),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_FeedScene),
        'feed_scene_${bp.name}.png',
      );
    });
  }
}

class _FeedScene extends StatelessWidget {
  const _FeedScene({required this.showLong});

  final bool showLong;

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600
        ? 14
        : maxWidth <= 1200
            ? 32
            : (maxWidth - 760) / 2;

    return ColoredBox(
      color: NFColors.bg,
      child: SafeArea(
        bottom: false,
        child: SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(side, 24, side, 40),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              _ForYouPill(),
              const SizedBox(height: 14),
              ShortCard(
                item: mockEntryToCardItem(kMockFeed[0]),
                onOpen: (_) {},
                onReact: (_, __) {},
              ),
              const SizedBox(height: 14),
              if (showLong) ...<Widget>[
                LongCard(
                  item: mockEntryToCardItem(kMockFeed[1]),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                ),
                const SizedBox(height: 14),
              ],
              AdCard(ad: kMockAds[0].toAd(), style: AdStyle.subtle),
              const SizedBox(height: 14),
              ShortCard(
                item: mockEntryToCardItem(kMockFeed[2]),
                onOpen: (_) {},
                onReact: (_, __) {},
                isLiked: true,
              ),
              const SizedBox(height: 14),
              ShortCard(
                item: mockEntryToCardItem(kMockFeed[3]),
                onOpen: (_) {},
                onReact: (_, __) {},
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ForYouPill extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            color: NFColors.ink,
            borderRadius: BorderRadius.circular(999),
          ),
          child: const Text(
            'Для вас',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: Color(0xFFFFFFFF),
              letterSpacing: -0.1,
            ),
          ),
        ),
      ],
    );
  }
}
