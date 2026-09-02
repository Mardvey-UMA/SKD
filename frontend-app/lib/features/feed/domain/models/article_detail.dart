import 'package:equatable/equatable.dart';

class ArticleDetail extends Equatable {
  const ArticleDetail({
    required this.id,
    required this.title,
    required this.body,
    required this.imageUrl,
    required this.author,
    required this.source,
    required this.publishedAt,
    this.isLiked = false,
    this.isDisliked = false,
    this.isSaved = false,
  });

  final String id;
  final String title;
  final String body;
  final String imageUrl;
  final String author;
  final String source;
  final DateTime publishedAt;
  final bool isLiked;
  final bool isDisliked;
  final bool isSaved;

  ArticleDetail copyWith({bool? isLiked, bool? isDisliked, bool? isSaved}) =>
      ArticleDetail(
        id: id,
        title: title,
        body: body,
        imageUrl: imageUrl,
        author: author,
        source: source,
        publishedAt: publishedAt,
        isLiked: isLiked ?? this.isLiked,
        isDisliked: isDisliked ?? this.isDisliked,
        isSaved: isSaved ?? this.isSaved,
      );

  @override
  List<Object?> get props => [
    id,
    title,
    body,
    imageUrl,
    author,
    source,
    publishedAt,
    isLiked,
    isDisliked,
    isSaved,
  ];
}
