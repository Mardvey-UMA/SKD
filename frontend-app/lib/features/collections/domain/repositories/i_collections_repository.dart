import '../../../feed/domain/models/content_item.dart';

class PaginatedContent {
  const PaginatedContent({
    required this.items,
    this.cursor,
    required this.hasNext,
  });

  final List<ContentItem> items;
  final String? cursor;
  final bool hasNext;

  static const empty = PaginatedContent(items: [], hasNext: false);
}

abstract interface class ICollectionsRepository {
  Future<PaginatedContent> getBookmarks({String? cursor});
  Future<PaginatedContent> getLikes({String? cursor});
  Future<PaginatedContent> getDislikes({String? cursor});
  Future<void> addBookmark(String contentId);
  Future<void> removeBookmark(String contentId);
  Future<void> addLike(String contentId);
  Future<void> removeLike(String contentId);
  Future<void> addDislike(String contentId);
  Future<void> removeDislike(String contentId);
}
