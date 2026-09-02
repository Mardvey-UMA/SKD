import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/interactions/domain/models/interaction_event.dart';

void main() {
  group('InteractionAction enum', () {
    test('all 6 values have correct apiValue strings', () {
      expect(InteractionAction.impression.apiValue, 'IMPRESSION');
      expect(InteractionAction.open.apiValue, 'OPEN');
      expect(InteractionAction.close.apiValue, 'CLOSE');
      expect(InteractionAction.like.apiValue, 'LIKE');
      expect(InteractionAction.dislike.apiValue, 'DISLIKE');
      expect(InteractionAction.bookmark.apiValue, 'BOOKMARK');
    });

    test('values list contains exactly 6 canonical actions', () {
      expect(InteractionAction.values.length, 6);
    });
  });

  group('InteractionEvent.toJson v2 fields', () {
    final baseTime = DateTime.utc(2026, 4, 20, 12, 0, 0);

    test('action_type serializes canonical apiValue string', () {
      final event = InteractionEvent(
        contentId: 'content-123',
        action: InteractionAction.impression,
        timestamp: baseTime,
      );

      expect(event.toJson()['action_type'], 'IMPRESSION');
    });

    test('impression → IMPRESSION in JSON', () {
      final event = InteractionEvent(
        contentId: 'c1',
        action: InteractionAction.impression,
        timestamp: baseTime,
      );
      expect(event.toJson()['action_type'], 'IMPRESSION');
    });

    test('open → OPEN in JSON', () {
      final event = InteractionEvent(
        contentId: 'c1',
        action: InteractionAction.open,
        timestamp: baseTime,
      );
      expect(event.toJson()['action_type'], 'OPEN');
    });

    test('close → CLOSE in JSON', () {
      final event = InteractionEvent(
        contentId: 'c1',
        action: InteractionAction.close,
        timestamp: baseTime,
      );
      expect(event.toJson()['action_type'], 'CLOSE');
    });

    test('like → LIKE in JSON', () {
      final event = InteractionEvent(
        contentId: 'c1',
        action: InteractionAction.like,
        timestamp: baseTime,
      );
      expect(event.toJson()['action_type'], 'LIKE');
    });

    test('dislike → DISLIKE in JSON', () {
      final event = InteractionEvent(
        contentId: 'c1',
        action: InteractionAction.dislike,
        timestamp: baseTime,
      );
      expect(event.toJson()['action_type'], 'DISLIKE');
    });

    test('bookmark → BOOKMARK in JSON', () {
      final event = InteractionEvent(
        contentId: 'c1',
        action: InteractionAction.bookmark,
        timestamp: baseTime,
      );
      expect(event.toJson()['action_type'], 'BOOKMARK');
    });

    test('toJson with all v2 fields populated returns correct JSON', () {
      final event = InteractionEvent(
        contentId: '550e8400-e29b-41d4-a716-446655440001',
        action: InteractionAction.open,
        timestamp: baseTime,
        feedRequestId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        positionInFeed: 3,
        deviceType: 'android',
        appVersion: '1.2.3',
        abBucket: 0,
        scrollDepth: 0.75,
        metadata: {'extra': 'value'},
      );

      final json = event.toJson();

      expect(json['schema_version'], 2);
      expect(json['content_id'], '550e8400-e29b-41d4-a716-446655440001');
      expect(json['action_type'], 'OPEN');
      expect(json['timestamp'], baseTime.toIso8601String());
      expect(json['feed_request_id'], 'a1b2c3d4-e5f6-7890-abcd-ef1234567890');
      expect(json['position_in_feed'], 3);
      expect(json['device_type'], 'android');
      expect(json['app_version'], '1.2.3');
      expect(json['ab_bucket'], 0);
      expect(json['scroll_depth'], 0.75);
      expect(json['metadata'], {'extra': 'value'});
    });

    test('toJson with only some v2 fields omits absent optional fields', () {
      final event = InteractionEvent(
        contentId: 'content-123',
        action: InteractionAction.impression,
        durationSec: 5.0,
        timestamp: baseTime,
        feedRequestId: 'req-id-abc',
        positionInFeed: 1,
      );

      final json = event.toJson();

      expect(json.containsKey('feed_request_id'), isTrue);
      expect(json['feed_request_id'], 'req-id-abc');
      expect(json.containsKey('position_in_feed'), isTrue);
      expect(json['position_in_feed'], 1);
      expect(json.containsKey('device_type'), isFalse);
      expect(json.containsKey('app_version'), isFalse);
      expect(json.containsKey('scroll_depth'), isFalse);
      expect(json.containsKey('metadata'), isFalse);
      expect(json['schema_version'], 2);
      expect(json['ab_bucket'], 0);
    });

    test('toJson with no v2 fields has schema_version:2 and ab_bucket:0 but no optional fields', () {
      final event = InteractionEvent(
        contentId: 'content-456',
        action: InteractionAction.close,
        timestamp: baseTime,
      );

      final json = event.toJson();

      expect(json['schema_version'], 2);
      expect(json['ab_bucket'], 0);
      expect(json.containsKey('feed_request_id'), isFalse);
      expect(json.containsKey('position_in_feed'), isFalse);
      expect(json.containsKey('device_type'), isFalse);
      expect(json.containsKey('app_version'), isFalse);
      expect(json.containsKey('scroll_depth'), isFalse);
      expect(json.containsKey('metadata'), isFalse);
      expect(json.containsKey('duration_sec'), isFalse);
    });

    test('toJson includes duration_sec when provided', () {
      final event = InteractionEvent(
        contentId: 'content-789',
        action: InteractionAction.impression,
        durationSec: 12.5,
        timestamp: baseTime,
      );

      final json = event.toJson();
      expect(json['duration_sec'], 12.5);
    });

    test('default abBucket is 0', () {
      final event = InteractionEvent(
        contentId: 'content-000',
        action: InteractionAction.open,
        timestamp: baseTime,
      );

      expect(event.abBucket, 0);
      expect(event.toJson()['ab_bucket'], 0);
    });
  });
}
