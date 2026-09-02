# Prompt 24 — PressScale micro-interaction · Report

**Phase:** 5 · Motion
**Slug:** redesign-p24-press-scale
**Date:** 2026-04-21

## What was built

New shared widget `PressScale` at `lib/ui/motion/press_scale.dart` that
encapsulates the `.tab-anim` CSS pattern from
`design/reference/mockup/radar-web.html`:

```css
.tab-anim { transition: transform 180ms ease; }
.tab-anim:active { transform: scale(0.92); }
```

### API

```dart
PressScale({
  double scale = 0.92,
  Duration duration = const Duration(milliseconds: 180),
  required Widget child,
  required VoidCallback onTap,
})
```

Implementation:
- Stateful, stores `_pressed` bool.
- `GestureDetector(behavior: HitTestBehavior.opaque, onTapDown/onTapUp/onTapCancel, onTap)`.
- `AnimatedScale(scale: _pressed ? scale : 1.0, duration: duration, curve: Curves.easeOut)`.
- Opaque hit-test — taps don't bubble (critical for reactions nested inside tappable cards).

## Call-sites migrated

All three inline `AnimatedScale` usages in `lib/ui/**` replaced with `PressScale`:

| File | Before | After |
|------|--------|-------|
| `lib/ui/nav/bottom_nav.dart` | `_BottomNavTabButton` was `StatefulWidget` tracking `_pressed`, wrapping `GestureDetector → AnimatedScale → AnimatedContainer` | Collapsed to `StatelessWidget` wrapping `PressScale → AnimatedContainer`. Behaviour identical. |
| `lib/ui/nav/side_nav.dart` | `_SideNavTabButton` tracked both `_hovered` and `_pressed` | Retains `_hovered` state (`MouseRegion`), drops `_pressed` — delegates to `PressScale`. `MouseRegion` wraps `PressScale` so hover tint survives. |
| `lib/ui/atoms/reaction_bar.dart` | `_ReactionButton` was `StatefulWidget` tracking `_pressed` | Collapsed to `StatelessWidget` wrapping `PressScale → AnimatedContainer`. |

Grep verification:

```
$ grep -rn 'AnimatedScale' lib/ui/
lib/ui/motion/press_scale.dart:14: (docstring reference)
lib/ui/motion/press_scale.dart:69: (implementation inside PressScale)
```

0 occurrences outside `press_scale.dart` itself — rule satisfied.

## InkWell ripple vs scale — decision

**Decision: scale-only, no InkWell layer.**

Grep `InkWell` across `lib/ui/**` returns **zero** matches — the neo-futurism
design language does not use Material `InkWell` ripples anywhere in the atoms
or nav layer. Every tappable surface already uses `GestureDetector` (now
encapsulated in `PressScale`) with the 0.92 press-down as the sole tactile
feedback. No widget currently layers both; no conflict to resolve.

Rationale:
- The mockup's `.tab-anim` CSS uses pure `transform: scale(0.92)` — no
  background flash, no ripple. Sticking to scale-only keeps the Flutter
  implementation 1:1 with the reference.
- `InkWell` requires a `Material` ancestor; introducing one inside the
  pill-shaped navs would mean `Material(type: transparency)` boilerplate for
  zero visual gain.
- Should any future CTA want a ripple, it should wrap `PressScale`'s child
  (not replace `PressScale`) — the scale is the primary affordance.

## Acceptance criteria

- [x] `lib/ui/motion/press_scale.dart` created with specified API.
- [x] `flutter analyze lib/ui/motion/press_scale.dart lib/ui/nav/bottom_nav.dart lib/ui/nav/side_nav.dart lib/ui/atoms/reaction_bar.dart test/ui/motion/press_scale_test.dart` — **No issues found**.
- [x] `flutter test test/ui/motion/press_scale_test.dart` — **3/3 passed** (onTap fires exactly once per tap; scale changes on press; respects custom scale/duration).
- [x] `flutter test test/ui/nav/ test/ui/atoms/` — **29/29 passed** (regression check; refactor did not break existing suites).
- [x] `grep -rn 'AnimatedScale' lib/ui/` returns only matches inside `press_scale.dart` itself.
- [x] InkWell-vs-scale decision documented (scale-only, zero InkWell in `lib/ui/`).

## Scope guard

- No changes to `lib/features/**` — respected `DO NOT touch` rule.
- No changes to `lib/shared/**` or `lib/widgets/**` — those live outside `lib/ui/**` and are out of scope for the redesign press_scale rule.
- No `flutter_hooks` introduced.

## Files

- Created: `lib/ui/motion/press_scale.dart`, `test/ui/motion/press_scale_test.dart`
- Edited: `lib/ui/nav/bottom_nav.dart`, `lib/ui/nav/side_nav.dart`, `lib/ui/atoms/reaction_bar.dart`
