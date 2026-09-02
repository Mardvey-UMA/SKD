import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'feed_provider.dart';
import 'user_interaction_cache_provider.dart';

part 'article_actions_provider.g.dart';

class ArticleActionState {
  const ArticleActionState({
    this.isLiked = false,
    this.isDisliked = false,
    this.isSaved = false,
  });

  final bool isLiked;
  final bool isDisliked;
  final bool isSaved;

  ArticleActionState copyWith({
    bool? isLiked,
    bool? isDisliked,
    bool? isSaved,
  }) => ArticleActionState(
    isLiked: isLiked ?? this.isLiked,
    isDisliked: isDisliked ?? this.isDisliked,
    isSaved: isSaved ?? this.isSaved,
  );
}

/// Provides per-article action state derived from the user interaction
/// cache. The notifier no longer holds a private map: instead it asks the
/// cache for any id and exposes a thin family-like API. Mutations call the
/// repository, then update the cache so every consumer (feed, collections,
/// related list, article detail) repaints immediately.
@riverpod
class ArticleActionsNotifier extends _$ArticleActionsNotifier {
  @override
  Map<String, ArticleActionState> build() {
    final cache = ref.watch(userInteractionCacheNotifierProvider);
    // Project the union of all known ids into ArticleActionState. This map
    // is consumed via .select((m) => m[id] ?? const ArticleActionState()),
    // so unknown ids fall back to the default and we don't need to enumerate
    // them up front.
    final ids = <String>{
      ...cache.likedIds,
      ...cache.dislikedIds,
      ...cache.bookmarkedIds,
    };
    return {
      for (final id in ids)
        id: ArticleActionState(
          isLiked: cache.isLiked(id),
          isDisliked: cache.isDisliked(id),
          isSaved: cache.isBookmarked(id),
        ),
    };
  }

  ArticleActionState stateFor(String id) =>
      state[id] ?? const ArticleActionState();

  Future<void> like(String articleId) async {
    final cache = ref.read(userInteractionCacheNotifierProvider.notifier);
    final wasLiked = ref
        .read(userInteractionCacheNotifierProvider)
        .isLiked(articleId);
    final repo = ref.read(feedRepositoryProvider);

    if (wasLiked) {
      cache.markUnliked(articleId);
      try {
        await repo.unlikeContent(articleId);
      } catch (_) {
        cache.markLiked(articleId);
      }
    } else {
      cache.markLiked(articleId);
      try {
        await repo.likeContent(articleId);
      } catch (_) {
        cache.markUnliked(articleId);
      }
    }
  }

  Future<void> dislike(String articleId) async {
    final cache = ref.read(userInteractionCacheNotifierProvider.notifier);
    final wasDisliked = ref
        .read(userInteractionCacheNotifierProvider)
        .isDisliked(articleId);
    final repo = ref.read(feedRepositoryProvider);

    if (wasDisliked) {
      cache.markUndisliked(articleId);
      try {
        await repo.undislikeContent(articleId);
      } catch (_) {
        cache.markDisliked(articleId);
      }
    } else {
      cache.markDisliked(articleId);
      try {
        await repo.dislikeContent(articleId);
      } catch (_) {
        cache.markUndisliked(articleId);
      }
    }
  }

  Future<void> toggleSave(String articleId) async {
    final cache = ref.read(userInteractionCacheNotifierProvider.notifier);
    final wasSaved = ref
        .read(userInteractionCacheNotifierProvider)
        .isBookmarked(articleId);
    final repo = ref.read(feedRepositoryProvider);

    if (wasSaved) {
      cache.markUnbookmarked(articleId);
      try {
        await repo.unbookmarkContent(articleId);
      } catch (_) {
        cache.markBookmarked(articleId);
      }
    } else {
      cache.markBookmarked(articleId);
      try {
        await repo.bookmarkContent(articleId);
      } catch (_) {
        cache.markUnbookmarked(articleId);
      }
    }
  }
}
