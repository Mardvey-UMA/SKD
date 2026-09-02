import 'package:equatable/equatable.dart';

sealed class ArticleCard extends Equatable {
  const ArticleCard({
    required this.id,
    required this.title,
    required this.source,
    required this.publishedAt,
    required this.likeCount,
  });

  final String id;
  final String title;
  final String source;
  final DateTime publishedAt;
  final int likeCount;
}

final class ImageArticleCard extends ArticleCard {
  const ImageArticleCard({
    required super.id,
    required super.title,
    required super.source,
    required super.publishedAt,
    required super.likeCount,
    required this.imageUrl,
  });

  final String imageUrl;

  @override
  List<Object?> get props => [
    id,
    title,
    source,
    publishedAt,
    likeCount,
    imageUrl,
  ];
}

final class TextArticleCard extends ArticleCard {
  const TextArticleCard({
    required super.id,
    required super.title,
    required super.source,
    required super.publishedAt,
    required super.likeCount,
    required this.excerpt,
    this.isSaved = false,
    this.readTimeMinutes = 5,
  });

  final String excerpt;
  final bool isSaved;
  final int readTimeMinutes;

  @override
  List<Object?> get props => [
    id,
    title,
    source,
    publishedAt,
    likeCount,
    excerpt,
    isSaved,
    readTimeMinutes,
  ];
}

final class DigestCard extends ArticleCard {
  const DigestCard({
    required super.id,
    required super.title,
    required super.source,
    required super.publishedAt,
    required super.likeCount,
    required this.summary,
  });

  final String summary;

  @override
  List<Object?> get props => [
    id,
    title,
    source,
    publishedAt,
    likeCount,
    summary,
  ];
}
