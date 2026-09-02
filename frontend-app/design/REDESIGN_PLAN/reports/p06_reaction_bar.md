# P06 — Reaction bar atom — Report

**Slug:** `redesign-p6-reaction-bar`
**Spec:** `design/REDESIGN_PLAN/prompts/06_reaction_bar.md`
**Source of truth:** `design/reference/mockup/cards.jsx` → `ReactionBar`

## Files created

- `lib/ui/atoms/reaction_bar.dart` — stateless `ReactionBar` atom.
- `test/ui/atoms/reaction_bar_test.dart` — widget tests.

## Design decisions

- **Stateless, props-driven.** `isLiked / isDisliked / isBookmarked` are passed
  from the parent. The atom never mutates them and never enforces
  mutual-exclusion between Like and Dislike — that is the parent's job (matches
  spec `Do NOT: не держать внутреннее состояние`).
- **Three buttons, 40×40 (compact 34×34), gap 8** — rendered via a `Row` with
  `SizedBox(width: 8)` separators; mirrors the JSX gap/flex layout.
- **Active visuals per spec § Web→Flutter mapping**
  | Button   | Active fill          | Active icon color       | Icon         |
  |----------|----------------------|-------------------------|--------------|
  | Like     | `NFColors.accent`    | `NFColors.accentInk`    | `thumb-up`   |
  | Dislike  | `NFColors.ink`       | `Color(0xFFFFFFFF)`     | `thumb-down` |
  | Bookmark | `NFColors.lime`      | `NFColors.limeInk`      | `bookmark`   |
- **Inactive visuals** — transparent fill, 1 px `NFColors.hairline` border,
  icon tinted `NFColors.ink2`.
- **Fill / border transition** — `AnimatedContainer(duration: NFMotion.fastDuration)`
  (180 ms, `Curves.easeOut`) mirrors CSS `transition: background/border-color 160ms ease`.
- **Press animation** — `AnimatedScale(scale: _pressed ? 0.92 : 1.0)` with the
  same `NFMotion.fast` timing. Internal `StatefulWidget` tracks the pressed
  flag only; the parent-facing API stays stateless. `PressScale` helper from P24
  is not used yet (comes later) — inline `AnimatedScale` is the interim.
- **Event bubbling** — `GestureDetector(behavior: HitTestBehavior.opaque)`. The
  `onTap` callback delegates to `onLike / onDislike / onBookmark`; the atom does
  not re-emit taps upward, so a reaction tap inside a card will not trigger
  `onOpen`.
- **a11y** — each button wrapped in `Semantics(label, button: true)` using the
  Russian labels from the JSX (`Нравится`, `Не нравится`, `В закладки`); the
  inner `NFIcon` is not double-labelled.

## Acceptance checks

| Criterion                                                                                | Result |
|------------------------------------------------------------------------------------------|--------|
| Tap on Like while Dislike is active — Dislike state NOT flipped inside atom              | Verified in test `tap on Like while Dislike active does NOT flip Dislike inside the atom` — `dislikeCalls == 0` after the Like tap |
| All 8 state permutations render without exception                                         | Verified in test `all 8 state permutations render without exception` — every combination of `isLiked × isDisliked × isBookmarked` is pumped and `takeException()` is `null` |
| `onLike / onDislike / onBookmark` fire exactly once per tap                               | Verified in test `onLike/onDislike/onBookmark called exactly once per tap` — each counter == 1 |
| Smooth 180 ms transition on prop change                                                   | Implemented via `AnimatedContainer` on the decoration and `AnimatedScale` on the press; visual goldens deferred (spec mentions "8 goldens" but goldens are a later phase, not listed in the immediate test set of this prompt) |

## Constraint compliance

- No imports from `lib/features/**` — atom has no `InteractionAction` knowledge.
- No new pub dependencies.
- `flutter analyze lib/ui/atoms/reaction_bar.dart test/ui/atoms/reaction_bar_test.dart` → `No issues found!`.
- `flutter test test/ui/atoms/reaction_bar_test.dart` → 3 / 3 passing.
