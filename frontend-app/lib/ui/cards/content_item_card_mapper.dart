import '../../core/config/media_config.dart';
import '../../core/sources/domain/source_type.dart';
import '../../features/feed/domain/models/content_item.dart';
import '../atoms/stripe_placeholder.dart';
import 'card_item.dart';

/// Shared mappers turning a domain [ContentItem] into a [CardItem] for the
/// redesigned feed shells (`ShortCard` / `LongCard`). Kept here so multiple
/// screens (feed, space detail, related, collections) render the same way.
bool isLongFormContent(ContentItem item) {
  final int? minutes = item.metadata?.readTimeMinutes;
  if (minutes != null && minutes >= 8) return true;
  final String? preview = item.bestPreview;
  return preview != null && preview.length >= 220;
}

CardItem contentItemToCardItem(ContentItem item) {
  final bool firstIsVideo = item.media.isNotEmpty &&
      item.media.first.type.toLowerCase() == 'video';
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
          : (resolvedImageUrls.length > 1 ? CardImages.multi : CardImages.one));
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

String _fallbackSource(String? sourceType) {
  final SourceType? t = SourceType.fromWire(sourceType);
  return t?.shortLabel ?? 'Источник';
}

StripeTone _toneFor(ContentItem item) {
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
