# Prompt 16 — Feed screen redesign

**Slug:** `redesign-p16-feed-screen`
**Branch:** `master` · **Spec:** `design/REDESIGN_PLAN/prompts/16_feed_screen.md`

## Files created

- `lib/features/feed/presentation/controllers/feed_screen_controller.dart`
  — `FeedScreenController` (`NotifierProvider`) owns presentation-only
  state: `hiddenAds` set, `adFrequency`. `buildFeedEntries()` helper
  computes the rendered sequence (content item / injected ad) — 1:1 port
  of the `useMemo` block in `design/reference/mockup/screens.jsx` →
  `FeedScreen`. Also exports the `kFeedAds` pool.
- `lib/screens/feed/feed_header.dart` — sticky header (logo-mark + «Радар»
  wordmark + «Для вас» ink pill). Padding is breakpoint-adaptive:
  mobile `18 / 62 / 18 / 14`, tablet+desktop `32 / 20 / 32 / 14`.
- `lib/screens/feed/feed_screen.dart` — new view. `CustomScrollView` +
  `SliverPersistentHeader` (pinned on tablet/desktop only — Risk flag #9:
  CanvasKit jitter on mobile pinned headers is avoided by re-mounting per
  paint). Renders `ShortCard` / `LongCard` / `AdCard` (Prompt 9) with
  `FeedSkeleton` (Prompt 10) and `EmptyState` for loading/empty branches,
  plus the mono footer `◦ КОНЕЦ ЛЕНТЫ · ПОТЯНИТЕ ДЛЯ ОБНОВЛЕНИЯ ◦`.
  Desktop content is centred in a `ConstrainedBox(maxWidth: 760)`.
- `lib/screens/feed/widgets/card_menu_launcher.dart` — `openCardMenu`
  helper that inserts the `CardMenu` (Prompt 15) into the root overlay so
  the sheet layers above `ResponsiveShell` chrome.

## Files edited

- `lib/core/router/app_router.dart` — import flipped from
  `features/feed/presentation/screens/feed_screen.dart` to
  `screens/feed/feed_screen.dart`. Route `/shell/feed` now builds the
  redesigned screen.

## Files deleted

- `lib/features/feed/presentation/screens/feed_screen.dart` — removed.
  Business logic that used to live here (scroll-controller-driven
  pagination, empty-feed retry, `loadFeed` on mount, IMPRESSION/OPEN
  emission from the wrapped `FeedCard`) moved to the new view + the
  per-card `_FeedItemView` widget inside `lib/screens/feed/feed_screen.dart`.

## Decision on controller extraction

Two-layer split:

- **Data-layer `FeedNotifier` is untouched** (pagination, refresh,
  retry, `feedRequestId`). Spec forbids touching state providers.
- **Presentation-layer `FeedScreenController`** owns the hidden-ad set
  and ad frequency only. It exposes `hideAd(adId)`; the computed entry
  sequence is produced by the pure `buildFeedEntries()` helper, which
  the widget calls inside `build()` (memoisation is naturally provided
  by Riverpod's state equality — rebuilds only fire when
  `feed.items` / `hiddenAds` / `adFrequency` actually change).
- **Per-card side-effects** — VisibilityDetector-driven IMPRESSION /
  CLOSE emission + OPEN on tap — live in a private
  `_FeedItemView` stateful widget inside the new screen file.
  The logic is a verbatim port of `lib/widgets/feed_card.dart`: the same
  threshold (0.5), the same ≥2 s rule for IMPRESSION vs CLOSE, the same
  `feed_request_id` / `position_in_feed` / `device_type` / `app_version`
  payload.

Rationale: the controller's only genuinely state-ful concern is the
hidden-ad set. Lifting the `VisibilityDetector` bookkeeping into the
controller would force per-card state into a notifier, which breaks
isolation between cards. Keeping per-card state at the widget layer
mirrors `FeedCard` 1:1 and keeps the controller pure.

## Data-capture invariants

All fields required by the MVP-hardening contract (`feed_request_id`,
`position_in_feed`, `device_type`, `app_version`, `ab_bucket`,
`scroll_depth`, `metadata`) continue to reach
`/api/interactions/batch` identically to the pre-redesign path:

- The `InteractionEvent` constructor is still called from the view
  layer; `ab_bucket` defaults to `0` exactly as before, `scroll_depth`
  + `metadata` are only set on CLOSE-from-article paths (unchanged —
  article detail screen owns those).
- `InteractionService` / `InteractionsBatcher` / `InteractionAction`
  enum / auth interceptors were **NOT** modified.
- `position_in_feed` is computed against the *content-item* sequence
  (ads skipped) via `FeedItemEntry.positionInFeed` — preserves parity
  with the legacy `widget.index + 1` formula.

## Ad-injection rules

Implemented in `buildFeedEntries`:

- `adFrequency >= 2 && items.length > 1` gates injection.
- Never injected at `i == 0` or when `i == items.length - 1` (last).
- Ads cycle through `kFeedAds` filtered by the hidden-set, ensuring no
  two ads are adjacent (each inject happens at a content slot).
- `hideAd(ad.brand)` updates the notifier; the next rebuild
  regenerates the sequence skipping the hidden brand.

## Sticky header implementation

Per Risk flag #9: `SliverPersistentHeader` (no `BackdropFilter`), with
`pinned: bp != Breakpoint.mobile`. On mobile, the header re-mounts on
every scroll paint — cheap and jitter-free under CanvasKit. Extent is
breakpoint-constant (mobile `108`, tablet/desktop `70`).

## Test adjustments

None required. All existing feed tests pass:

- `test/features/feed/data/**` — 13 tests pass
- `test/features/feed/domain/**` — 12 tests pass
- `test/features/feed/presentation/providers/**` — 4 tests pass
- `test/features/feed/presentation/widgets/feed_card_test.dart` — 6 tests
  pass (these only exercise `FeedState` / `feedNotifierProvider` — they
  do not instantiate the old screen widget, so the file deletion is
  invisible to them).

Pre-existing failures in
`test/features/feed/presentation/screens/article_detail_screen_test.dart`
(3 tests — pending-Timer assertion during HTML body rendering) are
unrelated to this prompt; they reproduce verbatim on `master`
pre-change (verified via `git stash`).

## Acceptance-criteria checklist

- [x] `flutter analyze lib/screens/feed/ lib/features/feed/presentation/controllers/` → **0 issues**.
- [x] Existing feed tests pass (widget-provider-level coverage
      unchanged; see above).
- [x] Data-capture invariants preserved (IMPRESSION / OPEN / CLOSE
      payload byte-identical to pre-redesign).
- [x] `ApiFeedRepository`, `InteractionsBatcher`, `FeedNotifier`,
      `InteractionAction` enum, auth interceptors — untouched.
- [x] No new packages added.
- [x] Router flipped to the new screen; `/shell/feed` now serves the
      redesigned view.
- [x] CardMenu (Prompt 15) wired via `openCardMenu(context, …)`
      helper using `Overlay.of(context, rootOverlay: true)`.
