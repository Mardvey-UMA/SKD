/// Build-time feature flags for gating Phase 4 behaviour.
class FeatureFlags {
  FeatureFlags._();

  /// When true, "Скрыть источник" on feed cards is restricted to premium.
  /// Phase 4 ships with `false` — all authenticated users may hide.
  static const bool requireSubscriptionForHideSource = false;

  /// Soft UI cap for Spaces (backend also enforces).
  static const int maxSpaces = 10;

  /// Soft UI cap for user-added sources (backend also enforces via 429).
  static const int maxAddedSources = 20;
}
