import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/reaction_bar.dart';

import '../golden_harness.dart';

/// State-matrix golden for `ReactionBar` — renders every meaningful
/// combination (all off / like only / dislike only / bookmark only /
/// like + bookmark / dislike + bookmark) plus the compact variant of each.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 6.
void main() {
  setUpAll(configureGoldenEnvironment);

  const List<_ReactionState> states = <_ReactionState>[
    _ReactionState(label: 'all-off', liked: false, disliked: false, bookmarked: false),
    _ReactionState(label: 'liked', liked: true, disliked: false, bookmarked: false),
    _ReactionState(label: 'disliked', liked: false, disliked: true, bookmarked: false),
    _ReactionState(label: 'bookmarked', liked: false, disliked: false, bookmarked: true),
    _ReactionState(label: 'liked-bookmarked', liked: true, disliked: false, bookmarked: true),
    _ReactionState(
      label: 'disliked-bookmarked',
      liked: false,
      disliked: true,
      bookmarked: true,
    ),
  ];

  testWidgets('reaction_bar state matrix golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const ColoredBox(
        color: NFColors.bg,
        child: Center(child: _ReactionMatrix(states: states, compact: false)),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(_ReactionMatrix),
      'reaction_bar_matrix.png',
    );
  });

  testWidgets('reaction_bar compact matrix golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const ColoredBox(
        color: NFColors.bg,
        child: Center(child: _ReactionMatrix(states: states, compact: true)),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(_ReactionMatrix),
      'reaction_bar_matrix_compact.png',
    );
  });
}

class _ReactionState {
  const _ReactionState({
    required this.label,
    required this.liked,
    required this.disliked,
    required this.bookmarked,
  });

  final String label;
  final bool liked;
  final bool disliked;
  final bool bookmarked;
}

class _ReactionMatrix extends StatelessWidget {
  const _ReactionMatrix({required this.states, required this.compact});

  final List<_ReactionState> states;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          for (final _ReactionState s in states) ...<Widget>[
            Text(
              s.label,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 11,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.8,
                color: NFColors.mute,
              ),
            ),
            const SizedBox(height: 6),
            ReactionBar(
              isLiked: s.liked,
              isDisliked: s.disliked,
              isBookmarked: s.bookmarked,
              onLike: () {},
              onDislike: () {},
              onBookmark: () {},
              compact: compact,
            ),
            const SizedBox(height: 14),
          ],
        ],
      ),
    );
  }
}
