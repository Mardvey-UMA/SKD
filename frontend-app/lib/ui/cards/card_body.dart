import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../atoms/nf_icon.dart';
import '../atoms/reaction_bar.dart';
import '../atoms/source_line.dart';
import 'card_item.dart';

/// Snippet rendering contract — mirrors the CSS difference between
/// `ShortCard` (uncapped snippet) and `LongCard` (clamped 96-px height
/// with a 40-px surface-to-transparent fade overlay).
class CardSnippet {
  const CardSnippet({
    required this.style,
    this.maxHeight,
    this.fadeOverlay = false,
  });

  final TextStyle style;

  /// When set, the snippet is wrapped in a `ClipRect` + `ConstrainedBox` and
  /// truncated to this height — matching `maxHeight: 96` in the mockup.
  final double? maxHeight;

  /// When `true`, a 40-px `Positioned.fill`-style gradient (transparent →
  /// [NFColors.surface]) is stacked over the bottom of the clamped snippet.
  final bool fadeOverlay;
}

/// Shared card-body block used by [ShortCard] / [LongCard].
///
/// Mirrors `CardBody` in `design/reference/mockup/cards.jsx` 1:1 while also
/// absorbing `LongCard`'s inline body variant — a single widget whose visual
/// differences are driven by [titleStyle] / [snippet] / [ctaLabel].
///
/// Composition (top → bottom):
///
/// 1. `SourceLine` — source name, time, optional readTime.
/// 2. Clickable title (tap → [onOpen]), 10-px top margin.
/// 3. Snippet text, 8-px top margin. Clamped + fade-overlayed when
///    [CardSnippet.maxHeight] is non-null.
/// 4. `ReactionBar` + inky CTA pill row, 14-px top margin, space-between.
///
/// The outer padding is `EdgeInsets.fromLTRB(18, 14, 18, 14)` per spec.
class CardBody extends StatelessWidget {
  const CardBody({
    super.key,
    required this.item,
    required this.titleStyle,
    required this.snippet,
    required this.ctaLabel,
    required this.onOpen,
    required this.onReact,
    this.isLiked = false,
    this.isDisliked = false,
    this.isBookmarked = false,
    this.onMore,
  });

  final CardItem item;
  final TextStyle titleStyle;
  final CardSnippet snippet;
  final String ctaLabel;

  final VoidCallback onOpen;
  final ValueChanged<ReactionKind> onReact;

  final bool isLiked;
  final bool isDisliked;
  final bool isBookmarked;
  final VoidCallback? onMore;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          SourceLine(
            source: item.source,
            time: item.time,
            readTime: item.readTime,
            onMore: onMore,
          ),
          // Telegram posts often ship with `title == ''` (backend returns
          // null → empty string in DTO). Skip the title block entirely so
          // there's no phantom gap above the snippet.
          if (item.title.isNotEmpty) ...<Widget>[
            const SizedBox(height: 10),
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: onOpen,
              child: Text(item.title, style: titleStyle),
            ),
          ],
          // Same for snippet — video posts may have no text at all.
          if (item.snippet.trim().isNotEmpty) ...<Widget>[
            SizedBox(height: item.title.isNotEmpty ? 8 : 10),
            _Snippet(text: item.snippet, config: snippet),
          ],
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              ReactionBar(
                isLiked: isLiked,
                isDisliked: isDisliked,
                isBookmarked: isBookmarked,
                onLike: () => onReact(ReactionKind.like),
                onDislike: () => onReact(ReactionKind.dislike),
                onBookmark: () => onReact(ReactionKind.bookmark),
              ),
              const Spacer(),
              _CtaPill(label: ctaLabel, onTap: onOpen),
            ],
          ),
        ],
      ),
    );
  }
}

class _Snippet extends StatelessWidget {
  const _Snippet({required this.text, required this.config});

  final String text;
  final CardSnippet config;

  @override
  Widget build(BuildContext context) {
    final Widget body = Text(text, style: config.style);

    if (config.maxHeight == null) {
      return body;
    }

    return ClipRect(
      child: SizedBox(
        height: config.maxHeight,
        child: Stack(
          fit: StackFit.expand,
          children: <Widget>[
            Align(
              alignment: Alignment.topLeft,
              child: body,
            ),
            if (config.fadeOverlay)
              const Align(
                alignment: Alignment.bottomCenter,
                child: _BottomFade(),
              ),
          ],
        ),
      ),
    );
  }
}

/// 40-px tall surface-to-transparent gradient sitting on the bottom of the
/// clamped snippet (mirrors `linear-gradient(to bottom, rgba(255,255,255,0),
/// surface)`).
class _BottomFade extends StatelessWidget {
  const _BottomFade();

  @override
  Widget build(BuildContext context) {
    return const IgnorePointer(
      child: SizedBox(
        height: 40,
        width: double.infinity,
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: <Color>[
                Color(0x00FFFFFF),
                NFColors.surface,
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CtaPill extends StatelessWidget {
  const _CtaPill({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
        decoration: const BoxDecoration(
          color: NFColors.ink,
          borderRadius: BorderRadius.all(Radius.circular(999)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(
              label,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 13,
                fontWeight: FontWeight.w600,
                letterSpacing: -0.2,
                color: Color(0xFFFFFFFF),
              ),
            ),
            const SizedBox(width: 6),
            const NFIcon('arrow-right', size: 13, color: Color(0xFFFFFFFF)),
          ],
        ),
      ),
    );
  }
}
