import 'content_item.dart';

class FeedState {
  const FeedState({
    this.items = const [],
    this.cursor,
    this.hasNext = true,
    this.isLoadingMore = false,
    this.isRefreshing = false,
    this.isLoaded = false,
    this.feedRequestId,
  });

  final List<ContentItem> items;
  final String? cursor;
  final bool hasNext;
  final bool isLoadingMore;
  final bool isRefreshing;

  /// True after at least one successful API response (even if empty).
  final bool isLoaded;

  /// X-Request-Id from the most recent GET /api/feed response.
  /// Used to attach feed attribution context to interaction events.
  final String? feedRequestId;

  FeedState copyWith({
    List<ContentItem>? items,
    Object? cursor = _sentinel,
    bool? hasNext,
    bool? isLoadingMore,
    bool? isRefreshing,
    bool? isLoaded,
    Object? feedRequestId = _sentinel,
  }) {
    return FeedState(
      items: items ?? this.items,
      cursor: cursor == _sentinel ? this.cursor : cursor as String?,
      hasNext: hasNext ?? this.hasNext,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      isLoaded: isLoaded ?? this.isLoaded,
      feedRequestId: feedRequestId == _sentinel
          ? this.feedRequestId
          : feedRequestId as String?,
    );
  }

  static const Object _sentinel = Object();
}

class ContentInteractionStatus {
  const ContentInteractionStatus({
    required this.liked,
    required this.disliked,
    required this.bookmarked,
  });

  final bool liked;
  final bool disliked;
  final bool bookmarked;

  factory ContentInteractionStatus.fromJson(Map<String, dynamic> json) {
    return ContentInteractionStatus(
      liked: json['liked'] as bool? ?? false,
      disliked: json['disliked'] as bool? ?? false,
      bookmarked: json['bookmarked'] as bool? ?? false,
    );
  }

  static const empty = ContentInteractionStatus(
    liked: false,
    disliked: false,
    bookmarked: false,
  );
}
