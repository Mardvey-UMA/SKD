import 'package:url_launcher/url_launcher.dart';

import '../../../../core/config/media_config.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../../../ui/atoms/stripe_placeholder.dart';
import '../../../../ui/cards/card_item.dart';
import '../../domain/models/content_item.dart';

/// Pure helpers shared by `DetailScreen` and `RelatedRail`.
///
/// All extracted from the pre-redesign `ArticleDetailScreen` /
/// `FeedScreen` so the new neo-futurism screens stay widget-only.
class DetailScreenController {
  const DetailScreenController._();

  /// Open the article's original URL in an external browser. No-op when
  /// [url] is empty or can't be parsed.
  static Future<void> openOriginal(String? url) async {
    if (url == null || url.isEmpty) return;
    final Uri? uri = Uri.tryParse(url);
    if (uri == null) return;
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  /// Convert a [ContentItem] into the shell [CardItem] consumed by
  /// `ShortCard` / `LongCard`. Mirrors `_toCardItem` in `feed_screen.dart`.
  static CardItem toCardItem(ContentItem item) {
    final bool firstIsVideo =
        item.media.isNotEmpty && item.media.first.type.toLowerCase() == 'video';
    final String? videoUrl = firstIsVideo
        ? MediaConfig.resolve(item.media.first.url)
        : null;
    final List<String> resolvedImageUrls = item.media
        .where((m) => m.type.toLowerCase() == 'image' || m.type.isEmpty)
        .map((m) => MediaConfig.resolve(m.url))
        .whereType<String>()
        .toList(growable: false);
    final CardImages images = firstIsVideo
        ? CardImages.one
        : (resolvedImageUrls.isEmpty
            ? CardImages.none
            : (resolvedImageUrls.length > 1
                ? CardImages.multi
                : CardImages.one));
    final StripeTone tone = _toneFor(item);
    final int seed = item.id.hashCode & 0x7fffffff;
    return CardItem(
      id: item.id,
      source: item.sourceName?.isNotEmpty == true
          ? item.sourceName!
          : item.authorName ?? _fallbackSource(item.sourceType),
      time: item.publishedAt ?? DateTime.now(),
      readTime: item.metadata?.readTimeMinutes != null
          ? '${item.metadata!.readTimeMinutes} мин чтения'
          : null,
      title: item.displayTitle,
      snippet: item.bestPreview ?? '',
      images: images,
      tone: tone,
      seed: seed,
      imageUrl: resolvedImageUrls.isNotEmpty ? resolvedImageUrls.first : null,
      imageUrls: resolvedImageUrls.take(10).toList(growable: false),
      isVideo: firstIsVideo,
      videoUrl: videoUrl,
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

  static String _fallbackSource(String? sourceType) {
    final SourceType? t = SourceType.fromWire(sourceType);
    return t?.shortLabel ?? 'Источник';
  }
}
