import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../theme/shadows.dart';
import '../atoms/image_item.dart';
import '../atoms/multi_image.dart';
import '../atoms/single_image.dart';
import '../atoms/video_play_overlay.dart';
import '../motion/feed_hero.dart';
import 'card_body.dart';
import 'card_item.dart';

/// Short card shell — image block (one / multi / none) + [CardBody] with
/// 21-px title, full-length muted snippet, and a `Читать` inky CTA.
///
/// Mirrors `ShortCard` in `design/reference/mockup/cards.jsx` 1:1. Tapping
/// the title or the CTA fires [onOpen]; tapping a reaction fires [onReact]
/// WITHOUT bubbling to [onOpen] (see `ReactionBar` `HitTestBehavior.opaque`).
///
/// Tokens (per spec):
/// * shell: `NFColors.surface` bg, `NFRadii.brLg` (26), hairline border,
///   `NFShadows.card`.
/// * image-block padding: 8 on all sides (skipped entirely when
///   `item.images == CardImages.none`).
/// * body: `EdgeInsets.fromLTRB(18, 14, 18, 14)`.
/// * title: 21 / w600 / -0.5 / line 1.15 / `NFColors.ink`.
/// * snippet: 14.5 / 1.5 / `NFColors.mute`, no clamp.
class ShortCard extends StatelessWidget {
  const ShortCard({
    super.key,
    required this.item,
    required this.onOpen,
    required this.onReact,
    this.isLiked = false,
    this.isDisliked = false,
    this.isBookmarked = false,
    this.onMore,
  });

  final CardItem item;

  final ValueChanged<CardItem> onOpen;
  final void Function(CardItem item, ReactionKind kind) onReact;

  final bool isLiked;
  final bool isDisliked;
  final bool isBookmarked;
  final VoidCallback? onMore;

  static const TextStyle _titleStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 21,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.5,
    height: 1.15,
    color: NFColors.ink,
  );

  static const TextStyle _snippetStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 14.5,
    fontWeight: FontWeight.w400,
    height: 1.5,
    color: NFColors.mute,
  );

  @override
  Widget build(BuildContext context) {
    // Tap anywhere on the card body / image to open the detail screen.
    // Reaction bar, the `…` menu and the CTA pill each intercept with
    // HitTestBehavior.opaque so their taps don't bubble here.
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => onOpen(item),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: NFRadii.brLg,
          border: Border.all(color: NFColors.hairline, width: 1),
          boxShadow: NFShadows.card,
        ),
        child: ClipRRect(
          borderRadius: NFRadii.brLg,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              if (item.images != CardImages.none)
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: FeedHero(
                    id: item.id,
                    child: _imageBlock(),
                  ),
                ),
              CardBody(
                item: item,
                titleStyle: _titleStyle,
                snippet: const CardSnippet(style: _snippetStyle),
                ctaLabel: 'Читать далее',
                onOpen: () => onOpen(item),
                onReact: (ReactionKind kind) => onReact(item, kind),
                isLiked: isLiked,
                isDisliked: isDisliked,
                isBookmarked: isBookmarked,
                onMore: onMore,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _imageBlock() {
    final ImageItem image = ImageItem(
      tone: item.tone,
      seed: item.seed,
      toneSecondary: item.toneSecondary,
      toneTertiary: item.toneTertiary,
      imageUrl: item.imageUrl,
      imageUrls: item.imageUrls,
      isVideo: item.isVideo,
    );
    final Widget base = item.images == CardImages.multi
        ? MultiImage(item: image)
        : SingleImage(item: image);
    if (item.isVideo) {
      return Stack(
        alignment: Alignment.center,
        children: <Widget>[base, const VideoPlayOverlay()],
      );
    }
    return base;
  }
}
