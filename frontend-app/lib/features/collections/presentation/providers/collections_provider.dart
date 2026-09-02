import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../../feed/domain/models/content_item.dart';
import '../../data/repositories/api_collections_repository.dart';
import '../../domain/repositories/i_collections_repository.dart';

part 'collections_provider.g.dart';

/// Provides the collections repository.
final collectionsRepositoryProvider = Provider<ICollectionsRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiCollectionsRepository(dio);
});

/// State for a paginated collection tab.
class CollectionTabState {
  const CollectionTabState({
    this.items = const [],
    this.cursor,
    this.hasNext = false,
    this.isLoadingMore = false,
    this.isRefreshing = false,
  });

  final List<ContentItem> items;
  final String? cursor;
  final bool hasNext;
  final bool isLoadingMore;
  final bool isRefreshing;

  CollectionTabState copyWith({
    List<ContentItem>? items,
    String? cursor,
    bool? hasNext,
    bool? isLoadingMore,
    bool? isRefreshing,
    bool clearCursor = false,
  }) => CollectionTabState(
    items: items ?? this.items,
    cursor: clearCursor ? null : (cursor ?? this.cursor),
    hasNext: hasNext ?? this.hasNext,
    isLoadingMore: isLoadingMore ?? this.isLoadingMore,
    isRefreshing: isRefreshing ?? this.isRefreshing,
  );
}

@Riverpod(keepAlive: true)
class BookmarksNotifier extends _$BookmarksNotifier {
  @override
  Future<CollectionTabState> build() async {
    final repo = ref.watch(collectionsRepositoryProvider);
    final page = await repo.getBookmarks();
    return CollectionTabState(
      items: page.items,
      cursor: page.cursor,
      hasNext: page.hasNext,
    );
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || current.isLoadingMore || !current.hasNext) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await ref
          .read(collectionsRepositoryProvider)
          .getBookmarks(cursor: current.cursor);
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          cursor: page.cursor,
          hasNext: page.hasNext,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }

  Future<void> refresh() async {
    final current = state.valueOrNull;
    if (current != null) {
      state = AsyncData(current.copyWith(isRefreshing: true));
    }
    try {
      final page = await ref.read(collectionsRepositoryProvider).getBookmarks();
      state = AsyncData(
        CollectionTabState(
          items: page.items,
          cursor: page.cursor,
          hasNext: page.hasNext,
        ),
      );
    } catch (_) {
      if (current != null) {
        state = AsyncData(current.copyWith(isRefreshing: false));
      }
    }
  }
}

@Riverpod(keepAlive: true)
class LikesNotifier extends _$LikesNotifier {
  @override
  Future<CollectionTabState> build() async {
    final repo = ref.watch(collectionsRepositoryProvider);
    final page = await repo.getLikes();
    return CollectionTabState(
      items: page.items,
      cursor: page.cursor,
      hasNext: page.hasNext,
    );
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || current.isLoadingMore || !current.hasNext) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await ref
          .read(collectionsRepositoryProvider)
          .getLikes(cursor: current.cursor);
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          cursor: page.cursor,
          hasNext: page.hasNext,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }

  Future<void> refresh() async {
    final current = state.valueOrNull;
    if (current != null) {
      state = AsyncData(current.copyWith(isRefreshing: true));
    }
    try {
      final page = await ref.read(collectionsRepositoryProvider).getLikes();
      state = AsyncData(
        CollectionTabState(
          items: page.items,
          cursor: page.cursor,
          hasNext: page.hasNext,
        ),
      );
    } catch (_) {
      if (current != null) {
        state = AsyncData(current.copyWith(isRefreshing: false));
      }
    }
  }
}

@Riverpod(keepAlive: true)
class DislikesNotifier extends _$DislikesNotifier {
  @override
  Future<CollectionTabState> build() async {
    final repo = ref.watch(collectionsRepositoryProvider);
    final page = await repo.getDislikes();
    return CollectionTabState(
      items: page.items,
      cursor: page.cursor,
      hasNext: page.hasNext,
    );
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || current.isLoadingMore || !current.hasNext) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await ref
          .read(collectionsRepositoryProvider)
          .getDislikes(cursor: current.cursor);
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          cursor: page.cursor,
          hasNext: page.hasNext,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }

  Future<void> refresh() async {
    final current = state.valueOrNull;
    if (current != null) {
      state = AsyncData(current.copyWith(isRefreshing: true));
    }
    try {
      final page = await ref.read(collectionsRepositoryProvider).getDislikes();
      state = AsyncData(
        CollectionTabState(
          items: page.items,
          cursor: page.cursor,
          hasNext: page.hasNext,
        ),
      );
    } catch (_) {
      if (current != null) {
        state = AsyncData(current.copyWith(isRefreshing: false));
      }
    }
  }
}
