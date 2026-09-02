# P10 · FeedSkeleton + EmptyState

**Spec:** `design/REDESIGN_PLAN/prompts/10_feed_skeleton_empty_state.md`
**Reference:** `design/reference/mockup/cards.jsx` → `FeedSkeleton`, `EmptyState`

## Files created

- `lib/ui/atoms/feed_skeleton.dart` — animated 3-card placeholder with shared shimmer sweep + lime pulse header.
- `lib/ui/atoms/empty_state.dart` — empty-list card with two concentric dashed rings + floating accent disk.
- `test/ui/atoms/feed_skeleton_test.dart` — 4 widget tests (card count, image block count, TickerMode muting, header caption).
- `test/ui/atoms/empty_state_test.dart` — 3 widget tests (full render, no-desc/no-action, lime default).

## `FeedSkeleton`

### Structure

```
VisibilityDetector(key: 'feed-skeleton')
└── TickerMode(enabled: _visible)
    └── _FeedSkeletonBody (StatefulWidget with TickerProviderStateMixin)
        ├── AnimationController _shimmer  — 1400ms, repeat()
        ├── AnimationController _pulse    — 1300ms, repeat(reverse: true)
        └── AnimatedBuilder(shimmer) → ShaderMask(blendMode: srcATop)
            └── Column
                ├── _Header(_pulse)
                ├── _SkeletonCard
                ├── _SkeletonCard
                └── _SkeletonCard
```

- **One shared `AnimationController`** drives shimmer for ALL three cards via a SINGLE `ShaderMask` — per the spec's explicit "DO NOT create per-card controllers" rule.
- `ShaderMask` uses `LinearGradient(surface2 → chipBg → surface2)` clamped at `TileMode.clamp`, with a 800-px-wide band whose left edge sweeps from `-400` to `+400` each 1400 ms cycle (matches the mockup's `@keyframes rdrShimmer` 800-px `background-size` + `-400 → 400` `background-position`).
- `blendMode: srcATop` keeps the shimmer painted only on the opaque parts of the placeholder shapes.

### Pulse dot

- 10×10 lime circle (`NFColors.lime`).
- Driven by a second shared controller (`_pulse`, 1300 ms `repeat(reverse: true)`) — value triangles `0 → 1 → 0`, we then eased with `Curves.easeInOut` inside an `AnimatedBuilder`.
- `scale = 1 + eased * 0.6` → reaches **1.6** at peak; `opacity = 1 - eased * 0.5` → dips to **0.5** at peak — exactly the mockup's `rdrPulse` keyframes.

### Header

- `NFText.mono('ВАША ЛЕНТА ФОРМИРУЕТСЯ...', color: ink2)` centered with the pulse dot (8-px gap).
- Sub-caption *"Подбираем свежие материалы по вашим интересам"* in plain `Nunito 13 / mute` beneath the mono line.
- Outer `EdgeInsets.fromLTRB(14, 14, 14, 6)` matches the JSX padding.

### Skeleton card

- Outer shell: `NFColors.surface` + `NFRadii.brLg` + 1-px hairline border + `EdgeInsets.all(8)` padding (matches the JSX `border-radius: NF.radiusLg, padding: 8`).
- Contents:
  - **Image block**: 180-tall `_Block(radius: NFRadii.radius)` filling the inner width.
  - **Text stack** (padding `10 14 10 6`):
    - 14-tall @ 45% width (`r=7`) — byline/meta line.
    - 10 gap.
    - 20-tall @ 85% width (`r=8`) — title line.
    - 8 gap.
    - 14-tall @ 70% width (`r=7`) — subtitle line.
    - 14 gap.
    - Row of three 36×36 circles (`r=999`) — reaction-bar placeholder.

### Viewport gating

- Outer `VisibilityDetector(key: 'feed-skeleton')` listens to `onVisibilityChanged`.
- `info.visibleFraction > 0` toggles `_visible`, which feeds `TickerMode(enabled: _visible)`.
- When parent scrolls the skeleton off-screen **OR** an ancestor explicitly wraps the atom in `TickerMode(enabled: false)`, the inner subtree's `TickerMode.valuesOf(ctx).enabled` becomes `false`, stopping the controllers via Flutter's ticker propagation.
- `visibility_detector: ^0.4.0` is already a declared pub dep (used by the feed-card impression tracker) — no new dependency.

### Do-NOT rules

- [x] No `shimmer` package — shimmer is hand-rolled via `ShaderMask` + one `AnimationController`.
- [x] No per-card `AnimationController`: three `_SkeletonCard` instances share ONE `_shimmer` controller + ONE `ShaderMask` that wraps the entire column.

## `EmptyState`

### API

```dart
EmptyState({
  String iconName = 'bookmark',
  required String title,
  String? desc,
  EmptyStateAccent accent = EmptyStateAccent.lime,
  Widget? action,
})
```

`EmptyStateAccent` enum maps to `(bg, fg)` colour pairs:

| accent   | bg (disk)             | fg (icon/text)          |
|----------|-----------------------|--------------------------|
| `lime`   | `NFColors.lime`       | `NFColors.limeInk`       |
| `accent` | `NFColors.accent`     | `NFColors.accentInk`     |
| `ink`    | `NFColors.ink`        | `#FFFFFF`                |

### Layout

- Outer `ClipRRect(NFRadii.brLg)` + `DecoratedBox(surface + hairline border)` + `EdgeInsets.fromLTRB(22, 36, 22, 36)` — matches the JSX `padding: 36px 22px`.
- Content (`Column(mainAxisSize: min)`):
  - **Badge 86×86** (see below).
  - `SizedBox(height: 16)`.
  - `Text(title)` — `Nunito 18 / w700 / letter -0.3 / ink`, centred.
  - (Optional) `SizedBox(height: 6)` → `ConstrainedBox(maxWidth: 260)` → `Text(desc)` — `Nunito 13.5 / line 1.5 / mute`, centred.
  - (Optional) `SizedBox(height: 18)` → user-supplied `action` widget.

### Badge — concentric dashed rings + floating disk

86×86 `Stack` with three `Positioned.fill` children:

1. **Outer ring** — `AnimatedBuilder` on a 16 s `repeat()` controller → `CustomPaint(_DashedRingPainter(rotation: v * 2π, inset: 0))`.
2. **Inner ring** — `AnimatedBuilder` on a 10 s `repeat()` controller → `CustomPaint(_DashedRingPainter(rotation: -v * 2π, inset: 12))`. Negative sign reverses the rotation.
3. **Floating disk** — `Padding(all: 24)` (`inset: 24` from the JSX) + `AnimatedBuilder` on a 3 s `repeat(reverse: true)` controller → `Transform.translate(dy: -4 * eased)` wrapping the 38×38 accent disk (`shape: circle`, `color: accent.bg`) with a centred `NFIcon(size: 22, color: accent.fg)`.

`_DashedRingPainter` manually loops `drawArc` segments (`dash = 4 px`, `gap = 4 px`, `stroke = 1.5 px`, colour `NFColors.hairline`). Dash count is rounded to distribute the remainder evenly across the circumference — no drift seam at rotation=0. Flutter does not natively expose `strokeDasharray`, hence the custom painter.

All three controllers are lifecycle-managed (`initState` → `repeat`, `dispose`). The badge sits inside an already-`TickerMode`-gated context when hosted inside the feed (parent scroll view); standalone use still respects `TickerMode.of` like every Flutter `vsync: this`.

## Quality gates

- `flutter analyze lib/ui/atoms/feed_skeleton.dart lib/ui/atoms/empty_state.dart test/ui/atoms/feed_skeleton_test.dart test/ui/atoms/empty_state_test.dart` → **No issues found!** (0.7 s).
- `flutter test test/ui/atoms/feed_skeleton_test.dart test/ui/atoms/empty_state_test.dart` → **+7 : All tests passed!**.
- `flutter test test/ui/` → **+46 : All tests passed!** — no regressions in P04–P09.

## Acceptance criteria

- [x] 3 placeholder skeleton cards rendered.
- [x] Shared shimmer: single `AnimationController` + single `ShaderMask` across all cards (spec-mandated).
- [x] Gradient sweep `-400 → 400 px`, linear, 1400 ms, infinite.
- [x] Header lime pulse-dot (scale 1 → 1.6 → 1 over 1300 ms, ease-in-out) + `NFText.mono('ВАША ЛЕНТА ФОРМИРУЕТСЯ...')`.
- [x] `VisibilityDetector` + `TickerMode(enabled: visible)` — animations freeze off-screen or when a parent `TickerMode(enabled: false)` wraps the atom (covered by `TickerMode.valuesOf` test).
- [x] `EmptyState({iconName, title, desc, accent, action})` with 86×86 disk + two dashed rings + 22-px centred `NFIcon`.
- [x] Outer 16 s rotation, inner 10 s reverse rotation (dashed rings via `CustomPainter`).
- [x] Centre disk floats `±4 px` over 3 s, title 18/w700/-0.3, desc 13.5/1.5 mute.
- [x] Optional primary pill action slot (accepts any `Widget?`).
- [x] No `shimmer` package; no per-card `AnimationController`.
- [ ] Static-frame-t=0 goldens — deferred; consistent with P04–P09 (golden infrastructure still pending).
