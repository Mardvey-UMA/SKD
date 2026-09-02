import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../auth/domain/entities/auth_state.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../collections/presentation/providers/collections_provider.dart';

part 'user_interaction_cache_provider.g.dart';

/// Snapshot of all interaction sets for the current user.
class UserInteractionCache {
  const UserInteractionCache({
    this.likedIds = const <String>{},
    this.dislikedIds = const <String>{},
    this.bookmarkedIds = const <String>{},
  });

  final Set<String> likedIds;
  final Set<String> dislikedIds;
  final Set<String> bookmarkedIds;

  bool isLiked(String id) => likedIds.contains(id);
  bool isDisliked(String id) => dislikedIds.contains(id);
  bool isBookmarked(String id) => bookmarkedIds.contains(id);

  UserInteractionCache copyWith({
    Set<String>? likedIds,
    Set<String>? dislikedIds,
    Set<String>? bookmarkedIds,
  }) => UserInteractionCache(
    likedIds: likedIds ?? this.likedIds,
    dislikedIds: dislikedIds ?? this.dislikedIds,
    bookmarkedIds: bookmarkedIds ?? this.bookmarkedIds,
  );

  static const empty = UserInteractionCache();
}

/// Lightweight cache of every content id the current user has liked /
/// disliked / bookmarked. Used as a single source of truth for action-bar
/// icon state across feed, collections, related list and article detail.
///
/// Loaded once on authentication by paginating through every collection
/// endpoint until [PaginatedContent.hasNext] is false. Mutations from the
/// action notifier (`like` / `dislike` / `toggleSave`) update the cache
/// optimistically so listeners (icons) repaint instantly.
@Riverpod(keepAlive: true)
class UserInteractionCacheNotifier extends _$UserInteractionCacheNotifier {
  @override
  UserInteractionCache build() {
    // Listen to auth state — load on login, clear on logout.
    ref.listen<AsyncValue<AuthState>>(authNotifierProvider, (prev, next) {
      final wasAuth = prev?.valueOrNull?.isAuthenticated ?? false;
      final isAuth = next.valueOrNull?.isAuthenticated ?? false;
      if (!wasAuth && isAuth) {
        // ignore: discarded_futures
        load();
      } else if (wasAuth && !isAuth) {
        clear();
      }
    });
    // If user is already authenticated when this provider is first read
    // (e.g. cold start with stored token), kick off loading immediately.
    final isAuth =
        ref.read(authNotifierProvider).valueOrNull?.isAuthenticated ?? false;
    if (isAuth) {
      // ignore: discarded_futures
      Future.microtask(load);
    }
    return UserInteractionCache.empty;
  }

  /// Loads every page of likes/dislikes/bookmarks in parallel and merges
  /// them into in-memory sets. Safe to call multiple times — replaces the
  /// existing snapshot atomically.
  Future<void> load() async {
    final repo = ref.read(collectionsRepositoryProvider);
    try {
      final results = await Future.wait([
        _collectAllIds(({String? cursor}) => repo.getLikes(cursor: cursor)),
        _collectAllIds(({String? cursor}) => repo.getDislikes(cursor: cursor)),
        _collectAllIds(({String? cursor}) => repo.getBookmarks(cursor: cursor)),
      ]);
      state = UserInteractionCache(
        likedIds: results[0],
        dislikedIds: results[1],
        bookmarkedIds: results[2],
      );
    } catch (_) {
      // On failure leave existing state alone — UI just won't have icons
      // pre-filled, but actions still work locally.
    }
  }

  Future<Set<String>> _collectAllIds(
    Future<dynamic> Function({String? cursor}) fetch,
  ) async {
    final ids = <String>{};
    String? cursor;
    var safetyCounter = 0;
    while (true) {
      // Defensive cap — never paginate more than 50 pages (~1000 items)
      // even if backend incorrectly always returns hasNext=true.
      if (safetyCounter++ > 50) break;
      final page = await fetch(cursor: cursor);
      for (final item in page.items) {
        ids.add(item.id as String);
      }
      if (page.hasNext != true || page.cursor == null) break;
      cursor = page.cursor as String;
    }
    return ids;
  }

  /// Marks an item as liked locally (and removes any prior dislike).
  void markLiked(String id) {
    state = state.copyWith(
      likedIds: {...state.likedIds, id},
      dislikedIds: state.dislikedIds.where((e) => e != id).toSet(),
    );
  }

  void markUnliked(String id) {
    state = state.copyWith(
      likedIds: state.likedIds.where((e) => e != id).toSet(),
    );
  }

  void markDisliked(String id) {
    state = state.copyWith(
      dislikedIds: {...state.dislikedIds, id},
      likedIds: state.likedIds.where((e) => e != id).toSet(),
    );
  }

  void markUndisliked(String id) {
    state = state.copyWith(
      dislikedIds: state.dislikedIds.where((e) => e != id).toSet(),
    );
  }

  void markBookmarked(String id) {
    state = state.copyWith(bookmarkedIds: {...state.bookmarkedIds, id});
  }

  void markUnbookmarked(String id) {
    state = state.copyWith(
      bookmarkedIds: state.bookmarkedIds.where((e) => e != id).toSet(),
    );
  }

  /// Wipes the cache (call on logout).
  void clear() {
    state = UserInteractionCache.empty;
  }
}
