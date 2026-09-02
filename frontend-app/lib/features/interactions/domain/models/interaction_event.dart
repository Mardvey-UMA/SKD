/// Canonical vocabulary for batch interaction events sent to
/// POST /api/interactions/batch.
///
/// Mapping from legacy backend strings:
///   VIEW        → impression
///   CLICK       → open
///   SCROLL_PAST → close
///   SAVE        → bookmark
///   HIDE        → dislike (legacy alias, mapped to same canonical value)
///   DISLIKE     → dislike
enum InteractionAction {
  impression('IMPRESSION'),
  open('OPEN'),
  close('CLOSE'),
  like('LIKE'),
  dislike('DISLIKE'),
  bookmark('BOOKMARK');

  const InteractionAction(this.apiValue);
  final String apiValue;
}

class InteractionEvent {
  const InteractionEvent({
    required this.contentId,
    required this.action,
    this.durationSec,
    required this.timestamp,
    this.feedRequestId,
    this.positionInFeed,
    this.deviceType,
    this.appVersion,
    this.abBucket = 0,
    this.scrollDepth,
    this.metadata,
  });

  final String contentId;
  final InteractionAction action;
  final double? durationSec;
  final DateTime timestamp;

  // v2 fields — all optional for backward compatibility
  /// UUID from the X-Request-Id header of the feed request that surfaced this
  /// content. Used to correlate interactions with specific feed generations.
  final String? feedRequestId;

  /// 1-indexed position of this content item in the feed response.
  final int? positionInFeed;

  /// Platform: "android" or "web".
  final String? deviceType;

  /// App version string from package_info_plus (e.g. "1.2.3").
  final String? appVersion;

  /// A/B experiment bucket number. Default 0 = control group.
  final int abBucket;

  /// Fraction [0.0, 1.0] of article scrolled. Relevant for CLOSE events.
  final double? scrollDepth;

  /// Open extension point for future fields.
  final Map<String, dynamic>? metadata;

  Map<String, dynamic> toJson() => {
    'content_id': contentId,
    'action_type': action.apiValue,
    if (durationSec != null) 'duration_sec': durationSec,
    'timestamp': timestamp.toUtc().toIso8601String(),
    'schema_version': 2,
    if (feedRequestId != null) 'feed_request_id': feedRequestId,
    if (positionInFeed != null) 'position_in_feed': positionInFeed,
    if (deviceType != null) 'device_type': deviceType,
    if (appVersion != null) 'app_version': appVersion,
    'ab_bucket': abBucket,
    if (scrollDepth != null) 'scroll_depth': scrollDepth,
    if (metadata != null) 'metadata': metadata,
  };
}
