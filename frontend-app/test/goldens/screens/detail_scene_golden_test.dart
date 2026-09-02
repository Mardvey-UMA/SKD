import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';
import 'package:frontend_app/ui/atoms/reaction_bar.dart';
import 'package:frontend_app/ui/atoms/single_image.dart';
import 'package:frontend_app/ui/atoms/image_item.dart';

import '../../fixtures/mockup_seed.dart';
import '../golden_harness.dart';

/// Detail screen — scene golden.
///
/// Renders the visual anatomy of `DetailScreen` (Prompt 17): hero image +
/// source line + H1 + body paragraphs + bottom reaction bar. Avoids the
/// real screen's providers (interactions / content detail) — wiring is
/// covered by widget tests in `test/features/feed/`.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 17.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('detail scene golden — ${bp.name}', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _DetailScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_DetailScene),
        'detail_scene_${bp.name}.png',
      );
    });
  }
}

class _DetailScene extends StatelessWidget {
  const _DetailScene();

  @override
  Widget build(BuildContext context) {
    final MockFeedEntry entry = kMockFeed[1]; // a2 — long form with body
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 18 : 32;
    final double contentMax = maxWidth <= 600 ? maxWidth : 720;

    return ColoredBox(
      color: NFColors.bg,
      child: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: contentMax),
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(side, 24, side, 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  _BackPill(),
                  const SizedBox(height: 18),
                  SingleImage(
                    item: ImageItem(tone: entry.tone, seed: entry.seed),
                  ),
                  const SizedBox(height: 18),
                  NFText.mono(
                    '${entry.source.toUpperCase()} · ${entry.time.toUpperCase()} · ${entry.read.toUpperCase()}',
                  ),
                  const SizedBox(height: 10),
                  Text(
                    entry.title,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 30,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -1,
                      height: 1.1,
                      color: NFColors.ink,
                    ),
                  ),
                  const SizedBox(height: 18),
                  const Text(
                    kMockFullBody,
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 15.5,
                      height: 1.6,
                      color: NFColors.ink2,
                    ),
                  ),
                  const SizedBox(height: 24),
                  ReactionBar(
                    isLiked: false,
                    isDisliked: false,
                    isBookmarked: false,
                    onLike: () {},
                    onDislike: () {},
                    onBookmark: () {},
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _BackPill extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: NFColors.surface,
        border: Border.all(color: NFColors.hairline),
        borderRadius: BorderRadius.circular(999),
      ),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          NFIcon('back', size: 14, color: NFColors.ink),
          SizedBox(width: 6),
          Text(
            'Назад',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 12.5,
              fontWeight: FontWeight.w600,
              color: NFColors.ink,
            ),
          ),
        ],
      ),
    );
  }
}
