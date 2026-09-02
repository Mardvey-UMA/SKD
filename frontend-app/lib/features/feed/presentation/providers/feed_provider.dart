import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../data/repositories/api_feed_repository.dart';
import '../../domain/models/content_item.dart';
import '../../domain/models/feed_state.dart';
import '../../domain/repositories/i_feed_repository.dart';
import '../../../auth/presentation/providers/dio_provider.dart';

part 'feed_provider.g.dart';

/// Provider for feed repository — uses real API implementation.
final feedRepositoryProvider = Provider<IFeedRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiFeedRepository(dio);
});

/// Provider for a single content item by ID.
final contentItemProvider = FutureProvider.autoDispose
    .family<ContentItem, String>((ref, contentId) {
      final repo = ref.watch(feedRepositoryProvider);
      return repo.getContentItem(contentId);
    });

@Riverpod(keepAlive: true)
class FeedNotifier extends _$FeedNotifier {
  /// In-flight loadMore future — used to prevent concurrent pagination calls.
  /// Unlike a simple boolean flag, storing the Future itself guarantees that
  /// even if _onScroll fires while the request is in-flight, we can await the
  /// same future rather than launching a duplicate request.
  Future<void>? _loadMoreFuture;

  @override
  FeedState build() => const FeedState();

  Future<void> loadFeed() async {
    if (state.isLoaded) return; // Already loaded
    state = const FeedState();
    try {
      final repo = ref.read(feedRepositoryProvider);
      final page = await repo.getFeed();
      state = FeedState(
        items: page.items,
        cursor: page.cursor,
        hasNext: page.hasNext,
        isLoaded: true,
        feedRequestId: page.requestId,
      );
    } catch (e) {
      state = state.copyWith(isLoaded: true);
      rethrow;
    }
  }

  /// Retry loading when feed was loaded but empty (recommendations forming).
  Future<void> retryEmpty() async {
    state = const FeedState(); // Reset isLoaded so loadFeed proceeds
    await loadFeed();
  }

  Future<void> loadMore() async {
    // If a loadMore is already running, skip.
    if (_loadMoreFuture != null) return;
    if (!state.hasNext || state.cursor == null) return;

    _loadMoreFuture = _doLoadMore();
    try {
      await _loadMoreFuture;
    } finally {
      _loadMoreFuture = null;
    }
  }

  Future<void> _doLoadMore() async {
    final cursorToLoad = state.cursor!;
    state = state.copyWith(isLoadingMore: true);
    try {
      final repo = ref.read(feedRepositoryProvider);
      final page = await repo.getFeed(cursor: cursorToLoad);

      // Deduplicate: only add items not already in the list
      final existingIds = state.items.map((e) => e.id).toSet();
      final newItems = page.items
          .where((item) => !existingIds.contains(item.id))
          .toList();

      state = state.copyWith(
        items: [...state.items, ...newItems],
        cursor: page.cursor,
        hasNext: page.hasNext,
        isLoadingMore: false,
        feedRequestId: page.requestId ?? state.feedRequestId,
      );
    } catch (e) {
      // On error: stop loading but keep hasNext so user can retry by scrolling.
      state = state.copyWith(isLoadingMore: false);
    }
  }

  Future<void> refresh() async {
    _loadMoreFuture = null;
    state = FeedState(isRefreshing: true);
    try {
      final repo = ref.read(feedRepositoryProvider);
      final page = await repo.getFeed(refresh: true);
      state = FeedState(
        items: page.items,
        cursor: page.cursor,
        hasNext: page.hasNext,
        isLoaded: true,
        feedRequestId: page.requestId,
      );
    } catch (e) {
      state = state.copyWith(isRefreshing: false, isLoaded: true);
      rethrow;
    }
  }

  /// Removes every card tied to the given source from the in-memory feed
  /// (used by "Скрыть источник"). No API call — pairs with
  /// `blockedSourcesNotifier.block` which handles persistence server-side.
  void removeBySource(String sourceId) {
    final filtered = state.items
        .where((item) => item.sourceId != sourceId)
        .toList();
    if (filtered.length == state.items.length) return;
    state = state.copyWith(items: filtered);
  }
}
