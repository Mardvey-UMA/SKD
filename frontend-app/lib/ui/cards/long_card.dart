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

/// Long-form card shell — image block (one / multi / none) + [CardBody] with
/// 22-px bold title, a 96-px-max snippet capped by a 40-px white-fade, and
/// a `Читать далее` inky CTA.
///
/// Mirrors `LongCard` in `design/reference/mockup/cards.jsx` 1:1. Unlike
/// [ShortCard] the outer shell is NOT tappable — `onOpen` fires only from
/// the title and the CTA pill. Reactions fire via [onReact] without
/// bubbling to [onOpen].
///
/// Tokens (per spec):
/// * shell: `NFColors.surface` bg, `NFRadii.brLg` (26), hairline border,
///   `NFShadows.card`.
/// * image-block padding: 8 on all sides (skipped when `images == none`).
/// * body: `EdgeInsets.fromLTRB(18, 14, 18, 14)`.
/// * title: 22 / w700 / -0.6 / line 1.14 / `NFColors.ink`.
/// * snippet: clamped `maxHeight: 96`, 14.5 / 1.5 / `NFColors.mute`,
///   40-px bottom gradient fade from transparent to [NFColors.surface].
class LongCard extends StatelessWidget {
  const LongCard({
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
    fontSize: 22,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.6,
    height: 1.14,
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
    // Tap anywhere on the card opens the detail screen; reactions / menu
    // have their own HitTestBehavior.opaque handlers so they don't bubble.
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
              snippet: const CardSnippet(
                style: _snippetStyle,
                maxHeight: 96,
                fadeOverlay: true,
              ),
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
