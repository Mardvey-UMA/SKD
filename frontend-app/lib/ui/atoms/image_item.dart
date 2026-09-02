import 'stripe_placeholder.dart';

/// Minimal model driving [SingleImage] and [MultiImage] atoms.
///
/// Mirrors the subset of the JS mockup `item` shape (`design/reference/mockup/
/// cards.jsx`) consumed by image variants: primary `tone`, optional secondary
/// and tertiary tones for the 3-cell layout, and a deterministic `seed` used
/// by [StripePlaceholder] for the `#NNN` corner label.
///
/// Optional [imageUrl] + [imageUrls] carry real media references when the
/// backend ships them (see `MediaConfig.resolve` for relative → absolute URL
/// resolution). When present, the image atoms render a `CachedNetworkImage`
/// on top of the stripe placeholder; when absent / empty / failed-to-load,
/// the stripe pattern shows as the final visual.
class ImageItem {
  const ImageItem({
    required this.tone,
    required this.seed,
    this.toneSecondary,
    this.toneTertiary,
    this.imageUrl,
    this.imageUrls = const <String>[],
    this.isVideo = false,
  });

  final StripeTone tone;
  final StripeTone? toneSecondary;
  final StripeTone? toneTertiary;
  final int seed;

  /// Absolute URL for the [SingleImage] variant, or `null` to keep the
  /// pure stripe placeholder.
  final String? imageUrl;

  /// Absolute URLs for the [MultiImage] 3-cell variant, rendered in
  /// positions 1/3, 2/3, 3/3 respectively. Missing entries fall back
  /// to the stripe placeholder of that cell's tone.
  final List<String> imageUrls;

  /// When `true`, the placeholder label reads «ВИДЕО» instead of
  /// «ФОТО» (the actual play-overlay is handled one layer up in
  /// `ShortCard._imageBlock` / `LongCard._imageBlock`).
  final bool isVideo;
}
