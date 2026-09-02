import '../models/content_item.dart';
import '../models/feed_state.dart';

class FeedPage {
  const FeedPage({
    required this.items,
    this.cursor,
    required this.hasNext,
    this.requestId,
  });

  final List<ContentItem> items;
  final String? cursor;
  final bool hasNext;

  /// The X-Request-Id header from the feed response.
  /// Used to attach feed attribution context to interaction events.
  final String? requestId;
}

abstract interface class IFeedRepository {
  Future<FeedPage> getFeed({String? cursor, bool refresh = false});
  Future<ContentItem> getContentItem(String contentId);
  Future<ContentInteractionStatus> getContentStatus(String contentId);
  Future<void> likeContent(String contentId);
  Future<void> unlikeContent(String contentId);
  Future<void> dislikeContent(String contentId);
  Future<void> undislikeContent(String contentId);
  Future<void> bookmarkContent(String contentId);
  Future<void> unbookmarkContent(String contentId);
}
