import '../atoms/stripe_placeholder.dart';

/// Image-block variant for [ShortCard] / [LongCard].
///
/// * [one] — single [StripePlaceholder] image.
/// * [multi] — 1-tall + 2-stacked tile grid (`MultiImage` atom).
/// * [none] — skips the image block entirely; no header padding is applied.
enum CardImages { one, multi, none }

/// Reaction kind fired by the card's `ReactionBar`.
///
/// Mirrors the three actions in `design/reference/mockup/cards.jsx` →
/// `ReactionBar` (`onLike` / `onDislike` / `onBookmark`). No counters, no
/// `share` / `save` labels.
enum ReactionKind { like, dislike, bookmark }

/// Minimal item model driving [ShortCard] / [LongCard] shells.
///
/// Mirrors the subset of the JS mockup `item` shape (`design/reference/mockup/
/// cards.jsx`) consumed by the card body + image block:
///
/// * `id` / `source` / `time` / optional `readTime` — meta row via `SourceLine`.
/// * `title` / `snippet` — body text.
/// * `images` — controls which image atom (if any) renders above the body.
/// * `tone` (+ optional secondary / tertiary) / `seed` — drive the
///   [StripePlaceholder] fill in `SingleImage` / `MultiImage`.
class CardItem {
  const CardItem({
    required this.id,
    required this.source,
    required this.time,
    required this.title,
    required this.snippet,
    required this.images,
    required this.tone,
    required this.seed,
    this.readTime,
    this.toneSecondary,
    this.toneTertiary,
    this.imageUrl,
    this.imageUrls = const <String>[],
    this.isVideo = false,
    this.videoUrl,
  });

  final String id;
  final String source;
  final DateTime time;
  final String? readTime;
  final String title;
  final String snippet;
  final CardImages images;
  final StripeTone tone;
  final StripeTone? toneSecondary;
  final StripeTone? toneTertiary;
  final int seed;

  /// Resolved absolute URL for the single-image variant, or `null`
  /// when the item has no media (the image atom then renders only a
  /// stripe placeholder).
  final String? imageUrl;

  /// Resolved absolute URLs for the multi-image variant (up to 3).
  final List<String> imageUrls;

  /// `true` when the first media entry is a video. Image block still
  /// renders a tone-matched stripe placeholder (until thumbnails land)
  /// but the card/detail overlays a centred play glyph and the detail
  /// screen uses `video_player` instead of `CachedNetworkImage`.
  final bool isVideo;

  /// Absolute URL of the first video entry (when [isVideo] is true).
  /// The detail screen feeds this to `VideoPlayerController.network`.
  final String? videoUrl;
}

/// Classifies the first media entry so cards and detail screens can
/// render the right kind of preview (image vs. video). Image atoms pick
/// `image` by default; cards with [isVideo] true still render a
/// placeholder image, plus a centred play-button overlay.
enum CardMediaKind { image, video }

