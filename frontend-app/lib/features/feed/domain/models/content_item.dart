class MediaItem {
  const MediaItem({
    required this.url,
    required this.type,
    this.width,
    this.height,
  });

  final String url;
  final String type; // "image", "video"
  final int? width;
  final int? height;
}

class ContentMetadata {
  const ContentMetadata({this.readTimeMinutes, this.tags = const []});

  final int? readTimeMinutes;
  final List<String> tags;
}

class ContentItem {
  const ContentItem({
    required this.id,
    required this.title,
    this.description,
    this.content,
    this.contentHtml,
    this.contentText,
    this.previewText,
    this.contentFormat,
    this.sourceId,
    this.sourceType,
    this.sourceSubtype,
    this.sourceName,
    this.url,
    this.publishedAt,
    this.authorName,
    this.media = const [],
    this.metadata,
    this.relatedIds = const [],
  });

  final String id;
  final String title;
  final String? description;
  final String? content;
  final String? contentHtml; // NEW: sanitized HTML (V1/V3)
  final String? contentText; // NEW: plain text (V2 caption / fallback)
  final String? previewText; // NEW: ≤300 char plain text for cards
  final String? contentFormat; // "HTML" or "PLAIN" (legacy: "text/html")
  final String? sourceId; // Phase 4: identifies the source for block/spaces.
  final String? sourceType; // "HABR", "VCRU", "TELEGRAM", "RSS"
  final String? sourceSubtype;
  final String? sourceName; // Phase 4: human-readable source label.
  final String? url;
  final DateTime? publishedAt;
  final String? authorName;
  final List<MediaItem> media;
  final ContentMetadata? metadata;
  final List<String> relatedIds;

  bool get isHtml => contentFormat?.toUpperCase() == 'HTML';
  bool get isTelegram => sourceType?.toUpperCase() == 'TELEGRAM';
  bool get hasMedia => media.isNotEmpty;
  MediaItem? get firstImage =>
      media.where((m) => m.type == 'image').firstOrNull;

  /// Title with HTML entities decoded for safe display in cards.
  String get displayTitle => _sanitiseText(title);

  /// Best preview text for cards: prefer server-provided preview_text (plain text),
  /// then description as fallback. Always run through a defensive HTML-tag stripper
  /// + entity decoder so stray `<p>`, `&gt;`, `&nbsp;`, etc. never leak into the card.
  String? get bestPreview {
    final preview = previewText?.trim();
    if (preview != null && preview.isNotEmpty) return _sanitiseText(preview);
    final desc = description?.trim();
    if (desc != null && desc.isNotEmpty) return _sanitiseText(desc);
    return null;
  }

  /// Strip HTML tags and decode common entities so cards show plain, readable text.
  static String _sanitiseText(String raw) {
    // Remove any HTML tags (`<p>`, `<a href=...>`, `<br/>`, etc.)
    final withoutTags = raw.replaceAll(RegExp(r'<[^>]*>'), ' ');
    // Decode the most common HTML entities produced by Habr/VC.RU exports.
    final decoded = withoutTags
        .replaceAll('&nbsp;', ' ')
        .replaceAll('\u00A0', ' ') // non-breaking space
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'")
        .replaceAll('&apos;', "'")
        .replaceAll('&laquo;', '«')
        .replaceAll('&raquo;', '»')
        .replaceAll('&mdash;', '—')
        .replaceAll('&ndash;', '–')
        .replaceAll('&hellip;', '…');
    // Collapse runs of whitespace
    return decoded.replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  /// Best display HTML for article body, may return null.
  String? get bestContentHtml => contentHtml ?? (isHtml ? content : null);

  /// Best plain text fallback for article body.
  String? get bestContentText => contentText ?? (isHtml ? null : content);
}
