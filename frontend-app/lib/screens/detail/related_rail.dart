import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/media_config.dart';
import '../../features/feed/domain/models/content_item.dart';
import '../../features/feed/presentation/providers/feed_provider.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/stripe_placeholder.dart';
import '../../ui/atoms/video_play_overlay.dart';

/// Horizontal rail of related articles. Mirrors `RelatedRail` in
/// `design/reference/mockup/screens.jsx` — 2.5 visible `ShortCard`s, a
/// «Смотреть все» chip that navigates to the existing related-list route,
/// and a trailing gutter for scroll-snap breathing room.
class RelatedRail extends ConsumerWidget {
  const RelatedRail({
    super.key,
    required this.parentId,
    required this.relatedIds,
  });

  final String parentId;
  final List<String> relatedIds;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (relatedIds.isEmpty) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.only(top: 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _RelatedRailHeader(
            total: relatedIds.length,
            onSeeAll: () =>
                context.push('/shell/feed/$parentId/related'),
          ),
          const SizedBox(height: 10),
          _RelatedRailList(relatedIds: relatedIds),
        ],
      ),
    );
  }
}

class _RelatedRailHeader extends StatelessWidget {
  const _RelatedRailHeader({
    required this.total,
    required this.onSeeAll,
  });

  final int total;
  final VoidCallback onSeeAll;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          const Expanded(
            child: Text(
              'Похожие материалы',
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 17,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.4,
                color: NFColors.ink,
              ),
            ),
          ),
          Semantics(
            label: 'Смотреть все похожие материалы',
            button: true,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: onSeeAll,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(
                    'Смотреть все ($total)',
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: NFColors.accent,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const NFIcon(
                    'arrow-right',
                    size: 13,
                    color: NFColors.accent,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _RelatedRailList extends StatelessWidget {
  const _RelatedRailList({required this.relatedIds});

  final List<String> relatedIds;

  @override
  Widget build(BuildContext context) {
    // 2.5 visible tiles: derive per-tile width from viewport width.
    final double viewport = MediaQuery.sizeOf(context).width;
    final double usable = viewport.clamp(320.0, 720.0) - 32;
    final double tileWidth = ((usable - 20) / 2.5).clamp(180.0, 260.0);

    // Compact teaser height: image (tileWidth × 0.66) + body (~70) + pads.
    // Keep the SizedBox tall enough for the thumbnail + 2-line title +
    // meta line so nothing overflows at any reasonable tile width.
    final double tileHeight = tileWidth * 0.66 + 88;

    return SizedBox(
      height: tileHeight,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsets.zero,
        physics: const BouncingScrollPhysics(),
        itemCount: relatedIds.length + 1,
        separatorBuilder: (_, _) => const SizedBox(width: 10),
        itemBuilder: (BuildContext context, int index) {
          if (index == relatedIds.length) {
            return const SizedBox(width: 16);
          }
          return SizedBox(
            width: tileWidth,
            child: _RelatedTeaser(contentId: relatedIds[index]),
          );
        },
      ),
    );
  }
}

/// Compact teaser card used by the related rail. Thumbnail + 2-line
/// title + source meta. No snippet, no reaction bar, no CTA — the
/// whole tile is tappable and navigates to the article.
class _RelatedTeaser extends ConsumerWidget {
  const _RelatedTeaser({required this.contentId});

  final String contentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<ContentItem> itemAsync =
        ref.watch(contentItemProvider(contentId));

    return itemAsync.when(
      loading: () => const _RelatedTeaserSkeleton(),
      error: (_, _) => const SizedBox.shrink(),
      data: (ContentItem item) => _RelatedTeaserContent(item: item),
    );
  }
}

class _RelatedTeaserContent extends StatelessWidget {
  const _RelatedTeaserContent({required this.item});

  final ContentItem item;

  @override
  Widget build(BuildContext context) {
    final bool isVideo = item.media.isNotEmpty &&
        item.media.first.type.toLowerCase() == 'video';
    final String? imageUrl = item.media
        .where((m) => m.type.toLowerCase() == 'image' || m.type.isEmpty)
        .map((m) => MediaConfig.resolve(m.url))
        .whereType<String>()
        .firstOrNull;
    final StripeTone tone = _toneFor(item);
    final int seed = item.id.hashCode & 0x7fffffff;
    final String title = item.displayTitle.trim().isNotEmpty
        ? item.displayTitle
        : (item.bestPreview ?? '').trim();
    final String source = item.sourceName?.isNotEmpty == true
        ? item.sourceName!
        : item.authorName ?? 'Источник';

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => context.push('/shell/feed/${item.id}'),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: NFRadii.brLg,
          border: Border.all(color: NFColors.hairline, width: 1),
        ),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              AspectRatio(
                aspectRatio: 16 / 10,
                child: _Thumb(
                  url: imageUrl,
                  isVideo: isVideo,
                  tone: isVideo ? StripeTone.ink : tone,
                  seed: seed,
                ),
              ),
              const SizedBox(height: 8),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 2),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    Text(
                      title.isEmpty ? source : title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 13.5,
                        fontWeight: FontWeight.w600,
                        height: 1.2,
                        color: NFColors.ink,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      source,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 11.5,
                        fontWeight: FontWeight.w500,
                        color: NFColors.mute,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static StripeTone _toneFor(ContentItem item) {
    const List<StripeTone> palette = <StripeTone>[
      StripeTone.ink,
      StripeTone.accent,
      StripeTone.lime,
      StripeTone.rose,
      StripeTone.violet,
      StripeTone.teal,
    ];
    return palette[item.id.hashCode.abs() % palette.length];
  }
}

class _Thumb extends StatelessWidget {
  const _Thumb({
    required this.url,
    required this.isVideo,
    required this.tone,
    required this.seed,
  });

  final String? url;
  final bool isVideo;
  final StripeTone tone;
  final int seed;

  @override
  Widget build(BuildContext context) {
    final Widget placeholder = StripePlaceholder(
      height: double.infinity,
      tone: tone,
      seed: seed,
      label: isVideo ? 'ВИДЕО' : 'ФОТО',
      radius: 0,
    );
    final Widget base = (url == null || url!.isEmpty)
        ? placeholder
        : CachedNetworkImage(
            imageUrl: url!,
            fit: BoxFit.cover,
            placeholder: (_, _) => placeholder,
            errorWidget: (_, _, _) => placeholder,
          );
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Stack(
        alignment: Alignment.center,
        fit: StackFit.expand,
        children: <Widget>[
          base,
          if (isVideo) const Center(child: VideoPlayOverlay(size: 44)),
        ],
      ),
    );
  }
}

class _RelatedTeaserSkeleton extends StatelessWidget {
  const _RelatedTeaserSkeleton();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.hairline, width: 1),
      ),
    );
  }
}
