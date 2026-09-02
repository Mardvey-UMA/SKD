import '../../domain/models/article_card.dart';

/// DTO stub for ArticleCard — unused; feed uses ContentItemDto via ApiFeedRepository.
class ArticleCardDto {
  const ArticleCardDto({
    required this.id,
    required this.type,
    required this.title,
    required this.source,
    required this.publishedAt,
    required this.likeCount,
    this.imageUrl,
    this.excerpt,
    this.readTimeMinutes,
    this.isSaved,
    this.summary,
  });

  final String id;
  final String type;
  final String title;
  final String source;
  final String publishedAt;
  final int likeCount;
  final String? imageUrl;
  final String? excerpt;
  final int? readTimeMinutes;
  final bool? isSaved;
  final String? summary;

  factory ArticleCardDto.fromJson(Map<String, dynamic> json) {
    return ArticleCardDto(
      id: json['id'] as String,
      type: json['type'] as String,
      title: json['title'] as String,
      source: json['source'] as String,
      publishedAt: json['published_at'] as String,
      likeCount: json['like_count'] as int,
      imageUrl: json['image_url'] as String?,
      excerpt: json['excerpt'] as String?,
      readTimeMinutes: json['read_time_minutes'] as int?,
      isSaved: json['is_saved'] as bool?,
      summary: json['summary'] as String?,
    );
  }

  ArticleCard toDomain() {
    final published = DateTime.parse(publishedAt);
    return switch (type) {
      'image' => ImageArticleCard(
        id: id,
        title: title,
        source: source,
        publishedAt: published,
        likeCount: likeCount,
        imageUrl: imageUrl ?? '',
      ),
      'text' => TextArticleCard(
        id: id,
        title: title,
        source: source,
        publishedAt: published,
        likeCount: likeCount,
        excerpt: excerpt ?? '',
        isSaved: isSaved ?? false,
        readTimeMinutes: readTimeMinutes ?? 5,
      ),
      'digest' => DigestCard(
        id: id,
        title: title,
        source: source,
        publishedAt: published,
        likeCount: likeCount,
        summary: summary ?? '',
      ),
      _ => TextArticleCard(
        id: id,
        title: title,
        source: source,
        publishedAt: published,
        likeCount: likeCount,
        excerpt: excerpt ?? '',
      ),
    };
  }
}
