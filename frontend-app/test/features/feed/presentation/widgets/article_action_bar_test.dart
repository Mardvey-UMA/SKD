/// Widget tests for [ArticleActionBar] tracking-event emission.
///
/// Covers the three regressions from the tracking-fix-e2e task:
/// * LIKE tap MUST emit `InteractionAction.like` (regression: was `.open`).
/// * DISLIKE tap MUST emit `InteractionAction.dislike`.
/// * BOOKMARK tap MUST emit `InteractionAction.bookmark`.
/// * All three emissions MUST propagate `feed_request_id` +
///   `position_in_feed` when provided by the parent feed card.
library;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/feed/domain/models/content_item.dart';
import 'package:frontend_app/features/feed/domain/models/feed_state.dart';
import 'package:frontend_app/features/feed/domain/repositories/i_feed_repository.dart';
import 'package:frontend_app/features/feed/presentation/providers/feed_provider.dart';
import 'package:frontend_app/features/feed/presentation/providers/user_interaction_cache_provider.dart';
import 'package:frontend_app/features/feed/presentation/widgets/article_action_bar.dart';
import 'package:frontend_app/features/interactions/data/services/interaction_service.dart';
import 'package:frontend_app/features/interactions/domain/models/interaction_event.dart';
import 'package:frontend_app/features/interactions/presentation/providers/interaction_service_provider.dart';

/// Minimal [InteractionService] that records events instead of batching
/// them to Dio. The batching pipeline is already covered by its own
/// unit tests — here we only care about what the widget *emitted*.
class _RecordingInteractionService extends InteractionService {
  _RecordingInteractionService() : super(Dio());

  final List<InteractionEvent> events = <InteractionEvent>[];

  @override
  void init() {
    // Intentionally skip WidgetsBindingObserver + timer — tests don't
    // need lifecycle hooks, and avoiding them keeps teardown simple.
  }

  @override
  void dispose() {
    // Skip super.dispose() — nothing to flush.
  }

  @override
  void trackEvent(InteractionEvent event) {
    events.add(event);
  }
}

/// No-op repo so `ArticleActionsNotifier.like/dislike/toggleSave()` don't
/// fire real HTTP requests. The test only cares about what [trackEvent]
/// receives — repo success/failure is irrelevant.
class _FakeFeedRepo implements IFeedRepository {
  @override
  Future<ContentItem> getContentItem(String contentId) async =>
      throw UnimplementedError();

  @override
  Future<FeedPage> getFeed({String? cursor, bool refresh = false}) =>
      throw UnimplementedError();

  @override
  Future<ContentInteractionStatus> getContentStatus(String contentId) =>
      throw UnimplementedError();

  @override
  Future<void> likeContent(String contentId) async {}

  @override
  Future<void> unlikeContent(String contentId) async {}

  @override
  Future<void> dislikeContent(String contentId) async {}

  @override
  Future<void> undislikeContent(String contentId) async {}

  @override
  Future<void> bookmarkContent(String contentId) async {}

  @override
  Future<void> unbookmarkContent(String contentId) async {}
}

/// Inline replacement for [UserInteractionCacheNotifier] that returns an
/// empty cache synchronously. Prevents the real notifier from watching
/// `authNotifierProvider` → `tokenCacheInitProvider`, which would spin up
/// a `FlutterSecureStorage` read and leave a pending Timer after the
/// widget tree is disposed.
class _FakeUserInteractionCacheNotifier
    extends UserInteractionCacheNotifier {
  @override
  UserInteractionCache build() => UserInteractionCache.empty;
}

Future<void> _pumpBar(
  WidgetTester tester, {
  required _RecordingInteractionService service,
  String articleId = 'article-1',
  String? feedRequestId,
  int? positionInFeed,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: <Override>[
        interactionServiceProvider.overrideWithValue(service),
        feedRepositoryProvider.overrideWithValue(_FakeFeedRepo()),
        userInteractionCacheNotifierProvider.overrideWith(
          _FakeUserInteractionCacheNotifier.new,
        ),
      ],
      child: MaterialApp(
        home: Scaffold(
          body: ArticleActionBar(
            articleId: articleId,
            feedRequestId: feedRequestId,
            positionInFeed: positionInFeed,
          ),
        ),
      ),
    ),
  );
}

void main() {
  group('ArticleActionBar — action-type emissions (regression from 33b0ab2)', () {
    testWidgets('LIKE tap emits InteractionAction.like (NOT .open)',
        (tester) async {
      final service = _RecordingInteractionService();
      await _pumpBar(tester, service: service);

      // The LIKE button is the first _ActionIconButton — tooltip "Нравится".
      await tester.tap(find.byTooltip('Нравится'));
      await tester.pump();

      expect(service.events, hasLength(1));
      expect(service.events.single.action, InteractionAction.like);
    });

    testWidgets('DISLIKE tap emits InteractionAction.dislike',
        (tester) async {
      final service = _RecordingInteractionService();
      await _pumpBar(tester, service: service);

      await tester.tap(find.byTooltip('Не нравится'));
      await tester.pump();

      expect(service.events, hasLength(1));
      expect(service.events.single.action, InteractionAction.dislike);
    });

    testWidgets('BOOKMARK tap emits InteractionAction.bookmark',
        (tester) async {
      final service = _RecordingInteractionService();
      await _pumpBar(tester, service: service);

      // Default (not-saved) tooltip is "Сохранить".
      await tester.tap(find.byTooltip('Сохранить'));
      await tester.pump();

      expect(service.events, hasLength(1));
      expect(service.events.single.action, InteractionAction.bookmark);
    });
  });

  group('ArticleActionBar — feed-context propagation', () {
    testWidgets('LIKE/DISLIKE/BOOKMARK emissions carry feedRequestId + positionInFeed',
        (tester) async {
      final service = _RecordingInteractionService();
      const String requestId = 'feed-req-uuid-abcd-1234';
      const int position = 7;

      await _pumpBar(
        tester,
        service: service,
        articleId: 'content-42',
        feedRequestId: requestId,
        positionInFeed: position,
      );

      await tester.tap(find.byTooltip('Нравится'));
      await tester.pump();
      await tester.tap(find.byTooltip('Не нравится'));
      await tester.pump();
      await tester.tap(find.byTooltip('Сохранить'));
      await tester.pump();

      expect(service.events, hasLength(3));
      for (final e in service.events) {
        expect(e.contentId, 'content-42');
        expect(e.feedRequestId, requestId);
        expect(e.positionInFeed, position);
      }
    });

    testWidgets('emissions without feed context (null) stay null',
        (tester) async {
      final service = _RecordingInteractionService();
      await _pumpBar(tester, service: service);

      await tester.tap(find.byTooltip('Нравится'));
      await tester.pump();

      expect(service.events.single.feedRequestId, isNull);
      expect(service.events.single.positionInFeed, isNull);
    });
  });
}
