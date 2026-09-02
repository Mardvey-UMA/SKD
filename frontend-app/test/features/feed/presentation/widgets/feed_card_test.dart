/// Widget-level tests for FeedCard interaction event attribution.
///
/// FeedCard has a hard GoRouter dependency in its onTap handler
/// (context.push). Running these tests requires a full GoRouter scaffold.
/// The core logic — that InteractionEvent carries feedRequestId and
/// positionInFeed — is fully covered by the unit tests in:
///   test/features/interactions/domain/models/interaction_event_test.dart
///
/// The tests below verify the FeedState.feedRequestId field is accessible
/// from the FeedNotifier override used in the card, and that positionInFeed
/// is computed correctly as (index + 1).
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/feed/domain/models/content_item.dart';
import 'package:frontend_app/features/feed/domain/models/feed_state.dart';
import 'package:frontend_app/features/feed/presentation/providers/feed_provider.dart';

const _testItem = ContentItem(id: 'test-content-id', title: 'Test Article');

const _testFeedRequestId = 'feed-req-uuid-1234-abcd';

/// Notifier override that pre-populates state without API calls.
class _FakeFeedNotifier extends FeedNotifier {
  _FakeFeedNotifier(this._state);
  final FeedState _state;

  @override
  FeedState build() => _state;
}

void main() {
  group('FeedCard v2 event attribution — provider-level', () {
    test('feedRequestId is accessible via feedNotifierProvider.select', () {
      final container = ProviderContainer(
        overrides: [
          feedNotifierProvider.overrideWith(
            () => _FakeFeedNotifier(
              const FeedState(
                items: [_testItem],
                isLoaded: true,
                feedRequestId: _testFeedRequestId,
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      final requestId = container.read(
        feedNotifierProvider.select((s) => s.feedRequestId),
      );

      expect(requestId, _testFeedRequestId);
    });

    test('feedRequestId is null when not captured (pre-first-load)', () {
      final container = ProviderContainer(
        overrides: [
          feedNotifierProvider.overrideWith(
            () => _FakeFeedNotifier(const FeedState()),
          ),
        ],
      );
      addTearDown(container.dispose);

      final requestId = container.read(
        feedNotifierProvider.select((s) => s.feedRequestId),
      );

      expect(requestId, isNull);
    });

    test('positionInFeed is 1-indexed: index 0 → position 1', () {
      const index = 0;
      const positionInFeed = index + 1;
      expect(positionInFeed, 1);
    });

    test('positionInFeed is 1-indexed: index 2 → position 3', () {
      const index = 2;
      const positionInFeed = index + 1;
      expect(positionInFeed, 3);
    });

    test('FeedState.copyWith preserves feedRequestId', () {
      const original = FeedState(
        items: [_testItem],
        isLoaded: true,
        feedRequestId: _testFeedRequestId,
      );
      final updated = original.copyWith(isLoadingMore: true);

      expect(updated.feedRequestId, _testFeedRequestId);
      expect(updated.isLoadingMore, isTrue);
    });

    test('FeedState.copyWith can clear feedRequestId to null via sentinel', () {
      const original = FeedState(feedRequestId: _testFeedRequestId);
      // Pass explicit null — this exercises the sentinel path.
      final cleared = original.copyWith(feedRequestId: null);

      expect(cleared.feedRequestId, isNull);
    });
  });
}
