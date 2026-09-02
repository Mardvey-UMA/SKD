import '../../domain/models/content_item.dart';

class MediaItemDto {
  const MediaItemDto({
    required this.url,
    required this.type,
    this.width,
    this.height,
  });

  final String url;
  final String type;
  final int? width;
  final int? height;

  factory MediaItemDto.fromJson(Map<String, dynamic> json) {
    // Backend ships two shapes:
    //   feed-service:  {"url": "...", "type": "image|video"}
    //   aggregator:    {"s3Url": "...", "mediaType": "IMAGE|VIDEO",
    //                   "mimeType": "image/jpeg|video/mp4"}
    // Normalise to a single `type` value: lowercase `image` / `video`.
    final String? explicitType = json['type'] as String?;
    final String? wireMediaType = json['mediaType'] as String?;
    final String? mime = json['mimeType'] as String?;
    final String resolved = (() {
      if (explicitType != null && explicitType.isNotEmpty) {
        return explicitType.toLowerCase();
      }
      if (wireMediaType != null && wireMediaType.isNotEmpty) {
        return wireMediaType.toLowerCase();
      }
      if (mime != null) {
        if (mime.startsWith('video/')) return 'video';
        if (mime.startsWith('image/')) return 'image';
      }
      return 'image';
    })();
    return MediaItemDto(
      url: (json['url'] as String?) ?? (json['s3Url'] as String?) ?? '',
      type: resolved,
      width: json['width'] as int?,
      height: json['height'] as int?,
    );
  }

  MediaItem toDomain() =>
      MediaItem(url: url, type: type, width: width, height: height);
}

class ContentMetadataDto {
  const ContentMetadataDto({this.readTimeMinutes, this.tags = const []});

  final int? readTimeMinutes;
  final List<String> tags;

  factory ContentMetadataDto.fromJson(Map<String, dynamic> json) {
    return ContentMetadataDto(
      readTimeMinutes: json['read_time_minutes'] as int?,
      tags:
          (json['tags'] as List<dynamic>?)?.map((e) => e as String).toList() ??
          const [],
    );
  }

  ContentMetadata toDomain() =>
      ContentMetadata(readTimeMinutes: readTimeMinutes, tags: tags);
}

class ContentItemDto {
  const ContentItemDto({
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
  final String? contentHtml;
  final String? contentText;
  final String? previewText;
  final String? contentFormat;
  final String? sourceId;
  final String? sourceType;
  final String? sourceSubtype;
  final String? sourceName;
  final String? url;
  final String? publishedAt;
  final String? authorName;
  final List<MediaItemDto> media;
  final ContentMetadataDto? metadata;
  final List<String> relatedIds;

  factory ContentItemDto.fromJson(Map<String, dynamic> json) {
    return ContentItemDto(
      id: json['id'] as String,
      title: (json['title'] as String?) ?? '',
      description: json['description'] as String?,
      content: json['content'] as String?,
      contentHtml: json['content_html'] as String?,
      contentText: json['content_text'] as String?,
      previewText: json['preview_text'] as String?,
      contentFormat: json['content_format'] as String?,
      sourceId: (json['source_id'] ?? json['sourceId']) as String?,
      sourceType: json['source_type'] as String?,
      sourceSubtype: json['source_subtype'] as String?,
      sourceName: (json['source_name'] ?? json['sourceName']) as String?,
      url: json['url'] as String?,
      publishedAt: json['published_at'] as String?,
      authorName: json['author_name'] as String?,
      media:
          (json['media'] as List<dynamic>?)
              ?.map((e) => MediaItemDto.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const [],
      metadata: json['metadata'] != null
          ? ContentMetadataDto.fromJson(
              json['metadata'] as Map<String, dynamic>,
            )
          : null,
      relatedIds:
          (json['related_ids'] as List<dynamic>?)
              ?.whereType<String>()
              .toList() ??
          const [],
    );
  }

  ContentItem toDomain() => ContentItem(
    id: id,
    title: title,
    description: description,
    content: content,
    contentHtml: contentHtml,
    contentText: contentText,
    previewText: previewText,
    contentFormat: contentFormat,
    sourceId: sourceId,
    sourceType: sourceType,
    sourceSubtype: sourceSubtype,
    sourceName: sourceName,
    url: url,
    publishedAt: publishedAt != null ? DateTime.tryParse(publishedAt!) : null,
    authorName: authorName,
    media: media.map((m) => m.toDomain()).toList(),
    metadata: metadata?.toDomain(),
    relatedIds: relatedIds,
  );
}
