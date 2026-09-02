import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/empty_state.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';
import 'package:frontend_app/ui/cards/short_card.dart';

import '../../fixtures/mockup_seed.dart';
import '../golden_harness.dart';

/// Bookmarks screen — scene golden. Renders the populated (`Сохранённые`)
/// state with 3 short cards drawn from the mockup seed.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 19.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('bookmarks scene golden — ${bp.name}',
        (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _BookmarksScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_BookmarksScene),
        'bookmarks_scene_${bp.name}.png',
      );
    });
  }

  testWidgets('bookmarks empty scene golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const _BookmarksEmptyScene(),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(_BookmarksEmptyScene),
      'bookmarks_empty_scene.png',
    );
  });
}

class _BookmarksScene extends StatelessWidget {
  const _BookmarksScene();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 14 : 32;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(side, 20, side, 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const NFText.mono('ЗАКЛАДКИ · 3'),
              const SizedBox(height: 8),
              const Text(
                'Сохранённые.',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 28,
                  height: 1.0,
                  letterSpacing: -1.1,
                  fontWeight: FontWeight.w700,
                  color: NFColors.ink,
                ),
              ),
              const SizedBox(height: 18),
              for (int i = 0; i < 3; i++) ...<Widget>[
                ShortCard(
                  item: mockEntryToCardItem(kMockFeed[i == 1 ? 2 : i * 2]),
                  onOpen: (_) {},
                  onReact: (_, __) {},
                  isBookmarked: true,
                ),
                const SizedBox(height: 14),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _BookmarksEmptyScene extends StatelessWidget {
  const _BookmarksEmptyScene();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: TickerMode(
          enabled: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 40, 14, 40),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const <Widget>[
                NFText.mono('ЗАКЛАДКИ · 0'),
                SizedBox(height: 8),
                Text(
                  'Сохранённые.',
                  style: TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 28,
                    height: 1.0,
                    letterSpacing: -1.1,
                    fontWeight: FontWeight.w700,
                    color: NFColors.ink,
                  ),
                ),
                SizedBox(height: 18),
                EmptyState(
                  iconName: 'bookmark',
                  accent: EmptyStateAccent.lime,
                  title: 'Здесь пока пусто',
                  desc:
                      'Сохраняйте интересное, и оно появится в этой подборке.',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
