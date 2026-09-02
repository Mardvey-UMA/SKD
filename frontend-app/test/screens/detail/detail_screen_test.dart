/// Widget tests for [DetailScreen] dispose-time interaction event.
///
/// Regression from commit 33b0ab2: dispose emitted `InteractionAction.open`
/// with `durationSec` (redundant second OPEN). Correct semantics is
/// `InteractionAction.close` + both `durationSec` and `scrollDepth` so the
/// rec-system `_classify_close()` can bucket full-read / half-read / bounce.
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
import 'package:frontend_app/features/interactions/data/services/interaction_service.dart';
import 'package:frontend_app/features/interactions/domain/models/interaction_event.dart';
import 'package:frontend_app/features/interactions/presentation/providers/interaction_service_provider.dart';
import 'package:frontend_app/screens/detail/detail_screen.dart';

/// Inline replacement that returns an empty cache synchronously, keeping
/// the real notifier's `ref.watch(authNotifierProvider)` chain out of the
/// test — otherwise the test leaves a pending Timer from
/// `FlutterSecureStorage.read().timeout(…)`.
class _FakeUserInteractionCacheNotifier
    extends UserInteractionCacheNotifier {
  @override
  UserInteractionCache build() => UserInteractionCache.empty;
}

class _RecordingInteractionService extends InteractionService {
  _RecordingInteractionService() : super(Dio());
  final List<InteractionEvent> events = <InteractionEvent>[];

  @override
  void init() {}

  @override
  void dispose() {}

  @override
  void trackEvent(InteractionEvent event) => events.add(event);
}

class _FakeRepo implements IFeedRepository {
  _FakeRepo(this.item);
  final ContentItem item;

  @override
  Future<ContentItem> getContentItem(String contentId) async => item;

  @override
  Future<FeedPage> getFeed({String? cursor, bool refresh = false}) =>
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

const _item = ContentItem(
  id: 'test-id',
  title: 'Test Article',
  description: 'Short body that fits on one screen.',
);

Widget _buildApp(_RecordingInteractionService service) {
  return ProviderScope(
    overrides: <Override>[
      feedRepositoryProvider.overrideWithValue(_FakeRepo(_item)),
      interactionServiceProvider.overrideWithValue(service),
      userInteractionCacheNotifierProvider.overrideWith(
        _FakeUserInteractionCacheNotifier.new,
      ),
    ],
    child: const MaterialApp(home: DetailScreen(articleId: 'test-id')),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('DetailScreen.dispose emits CLOSE (regression from 33b0ab2)', () {
    testWidgets('dispose emits InteractionAction.close (NOT .open)',
        (tester) async {
      final service = _RecordingInteractionService();
      await tester.pumpWidget(_buildApp(service));
      await tester.pumpAndSettle();

      // Unmount the screen to trigger dispose().
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();

      expect(service.events, hasLength(1));
      final InteractionEvent event = service.events.single;
      expect(event.action, InteractionAction.close);
      expect(event.contentId, 'test-id');
    });

    testWidgets('CLOSE event includes durationSec (>= 0) and scrollDepth',
        (tester) async {
      final service = _RecordingInteractionService();
      await tester.pumpWidget(_buildApp(service));
      await tester.pumpAndSettle();

      // Advance clock a bit so durationSec > 0.
      await tester.pump(const Duration(milliseconds: 200));

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();

      expect(service.events, hasLength(1));
      final InteractionEvent event = service.events.single;
      expect(event.durationSec, isNotNull);
      expect(event.durationSec! >= 0, isTrue);
      expect(event.scrollDepth, isNotNull);
      // Fraction bounds.
      expect(event.scrollDepth! >= 0.0 && event.scrollDepth! <= 1.0, isTrue);
    });
  });
}
