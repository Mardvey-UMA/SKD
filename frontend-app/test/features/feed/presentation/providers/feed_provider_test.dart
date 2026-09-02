import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/feed/domain/models/content_item.dart';
import 'package:frontend_app/features/feed/domain/models/feed_state.dart';
import 'package:frontend_app/features/feed/domain/repositories/i_feed_repository.dart';
import 'package:frontend_app/features/feed/presentation/providers/feed_provider.dart';

class _FakeFeedRepository implements IFeedRepository {
  Completer<FeedPage> completer = Completer<FeedPage>();

  @override
  Future<FeedPage> getFeed({String? cursor, bool refresh = false}) =>
      completer.future;

  @override
  Future<ContentItem> getContentItem(String contentId) =>
      throw UnimplementedError();

  @override
  Future<ContentInteractionStatus> getContentStatus(String contentId) =>
      throw UnimplementedError();

  @override
  Future<void> likeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> unlikeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> dislikeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> undislikeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> bookmarkContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> unbookmarkContent(String contentId) =>
      throw UnimplementedError();
}

void main() {
  group('FeedNotifier.refresh()', () {
    test('clears items immediately before fetch completes', () async {
      final fakeRepo = _FakeFeedRepository();

      final container = ProviderContainer(
        overrides: [feedRepositoryProvider.overrideWithValue(fakeRepo)],
      );
      addTearDown(container.dispose);

      final notifier = container.read(feedNotifierProvider.notifier);

      // Start refresh (non-awaited) — completer not yet resolved
      final refreshFuture = notifier.refresh();

      // After calling refresh(), items should be [] immediately
      await Future.microtask(() {});
      final stateAfterRefreshStart = container.read(feedNotifierProvider);
      expect(
        stateAfterRefreshStart.items,
        isEmpty,
        reason: 'items must be cleared immediately when refresh() is called',
      );
      expect(
        stateAfterRefreshStart.isLoaded,
        isFalse,
        reason: 'isLoaded must be false while refreshing',
      );

      // Now complete the fetch
      fakeRepo.completer.complete(
        FeedPage(
          items: [const ContentItem(id: 'new1', title: 'New Article')],
          hasNext: false,
        ),
      );

      await refreshFuture;

      final finalState = container.read(feedNotifierProvider);
      expect(finalState.items, hasLength(1));
      expect(finalState.items.first.id, 'new1');
      expect(finalState.isLoaded, isTrue);
    });

    test(
      'items are empty and isLoaded=false during refresh (before response)',
      () async {
        final fakeRepo = _FakeFeedRepository();

        final container = ProviderContainer(
          overrides: [feedRepositoryProvider.overrideWithValue(fakeRepo)],
        );
        addTearDown(container.dispose);

        final notifier = container.read(feedNotifierProvider.notifier);

        // Start refresh without resolving completer
        // ignore: unawaited_futures
        notifier.refresh();

        await Future.microtask(() {});

        final s = container.read(feedNotifierProvider);
        expect(s.items, isEmpty);
        expect(s.isLoaded, isFalse);
        expect(s.isRefreshing, isTrue);

        // Cleanup: complete to avoid unhandled future
        fakeRepo.completer.complete(const FeedPage(items: [], hasNext: false));
      },
    );
  });
}
