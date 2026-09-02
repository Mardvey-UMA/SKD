# P09 · AdCard: subtle / card / banner + HatchedPainter

**Spec:** `design/REDESIGN_PLAN/prompts/09_ad_card.md`
**Reference:** `design/reference/mockup/cards.jsx` → `AdCard`

## Files created

- `lib/models/ad.dart` — `Ad(brand, tagline, title, desc, cta)` data class.
- `lib/ui/atoms/hatched_painter.dart` — reusable `CustomPainter` for the 135° diagonal hatch (card-header + banner-tile, earmarked for P18 collection tiles).
- `lib/ui/cards/ad_card.dart` — `AdStyle` enum + `AdCard` widget covering all three visible styles.
- `test/ui/cards/ad_card_test.dart` — 11 widget tests (enum + 4 render + 6 callback).

## Data model — `Ad`

```dart
class Ad {
  const Ad({
    required String brand,
    required String tagline,
    required String title,
    required String desc,
    required String cta,
  });
}
```

All fields are plain `String`; immutable (`const` constructible). No JSON serialization required at this phase — the spec does not bind AdCard to any backend contract.

## `AdStyle` enum

```dart
enum AdStyle { off, subtle, card, banner }
```

- `off` — feature-flag value; widget returns `SizedBox.shrink()`.
- `subtle` — 4-px lime accent strip on the left edge of a regular surface card.
- `card` — 1.5-px lime border + 88-px hatched lime header with a giant brand wordmark.
- `banner` — one-row banner with a 54×54 lime hatched icon tile and a 26-px close button.

## `HatchedPainter` (new reusable atom)

Reproduces the CSS `repeating-linear-gradient(135deg, transparent 0 Xpx, rgba(14,15,13,α) Xpx Ypx)` via `CustomPainter`. Parametrized:

| Param | Default (card header) | Banner tile |
|-------|-----------------------|-------------|
| `period` | 14 | 9 |
| `strokeWidth` | 2 | 1 |
| `color` | `rgba(14,15,13,0.07)` (`0x120E0F0D`) | `rgba(14,15,13,0.08)` (`0x140E0F0D`) |

Drawing runs from top-left to bottom-right using a `Canvas.clipRect` + line-draw loop with step = `period * sqrt(2)`. The painter is clipped by its host `ClipRRect`, so the corners honor the `NFRadii.brLg` / 14-px tile radius.

## `subtle` variant

- Shell: `NFColors.surface` bg, `NFRadii.brLg` (26) border-radius, 1-px `NFColors.hairline` border.
- Left strip: `Positioned(left: 0, top: 0, bottom: 0, width: 4)` with `ColoredBox(NFColors.lime)`.
- Padding: `EdgeInsets.fromLTRB(18, 14, 14, 12)` (matches spec literally).
- Row 1: `_AdLabel` — 5×5 lime dot + `NFText.mono('РЕКЛАМА · BRAND')` + 28×28 close (right-aligned via `Expanded`).
- Title: `Nunito 18 / w700 / letter -0.4 / height 1.25 / ink`.
- Desc (uses `ad.tagline`): `Nunito 13.5 / w400 / height 1.45 / mute`.
- CTA pill: ink-filled, `999px` radius, 16×10 padding, `Nunito 13.5 / w600 / -0.2 / #FFF` label + `arrow-right` 13-px icon.

## `card` variant

- Shell: 1.5-px `NFColors.lime` border; `ClipRRect(NFRadii.brLg)` so the header fills corner-to-corner with no bleed.
- Header (88 px): `ColoredBox(NFColors.lime)` + `CustomPaint(HatchedPainter())` overlay.
- Giant wordmark: `Text(ad.brand)` at `Nunito 34 / w800 / letter -1.2 / line 1.0 / NFColors.limeInk`, opacity 0.85, anchored `right: 14, top: 14`.
- Body padding: `EdgeInsets.fromLTRB(14, 12, 14, 14)`.
- Body composition identical to `subtle` (label → title 18/w700/-0.4 → desc 13.5/mute → pill).
- `desc` uses `ad.desc` (not `ad.tagline`) per spec — same as the JSX mockup.

## `banner` variant

- Shell: `NFRadii.brLg` surface + hairline border; padding `12×10`.
- `_BannerTile`: 54×54 square, `BorderRadius.circular(14)`, lime bg, hatched via `HatchedPainter(period: 9, strokeWidth: 1, color: 0x140E0F0D)`. `Text(ad.brand[0].toUpperCase())` at `Nunito 22 / w800 / letter -0.6 / limeInk`, centered on top of the hatch.
- Middle column (`Expanded`): mono label with 5×5 lime dot → `title 15 / w700 / -0.3 / line 1.3` `ellipsis` → `tagline 12.5 / w400 / line 1.35 / mute` `ellipsis`.
- Close button: 26×26, 13-px icon, right side.

The outer `Row` uses `crossAxisAlignment: center` so the 54-px tile, title column, and close button align at vertical center.

## Callbacks — `onClick` / `onHide`

- `onClick(ad)` — wires to a top-level `GestureDetector(behavior: opaque, onTap: () => onClick(ad))` wrapping the whole card content.
- `onHide(ad)` — wired to the close button via a **nested** `GestureDetector(behavior: opaque)`. Because inner opaque hit-testers consume pointer events before they reach outer detectors, tapping the close button does NOT trigger `onClick` — matching the JSX `e.stopPropagation()` contract. Validated by all three «tap close fires onHide and NOT onClick» tests.
- `onClick`/`onHide` are both optional (`ValueChanged<Ad>?`) — passing `null` disables the respective gesture (the detector's `onTap` is `null`, so the region is not hit-testable for that event).

## Tests — 11/11 green

`test/ui/cards/ad_card_test.dart`:

1. `AdStyle.values == [off, subtle, card, banner]` (exact order).
2. `renders subtle without exception` — title + tagline found.
3. `renders card without exception` — desc (body) + brand wordmark found.
4. `renders banner without exception` — title + tagline + first-letter tile.
5. `AdStyle.off` — no visible text (shrunk to zero).
6. `tap close fires onHide NOT onClick (subtle)` — `hides=1, clicks=0`.
7. Same for `card`.
8. Same for `banner`.
9. `tap on body fires onClick (subtle)` — `clicks=1`, receives the exact `Ad` instance.
10. Same for `card`.
11. Same for `banner`.

Close button is hit-tested via `find.bySemanticsLabel('Скрыть рекламу')` — the `_CloseButton` wraps its tap target in a `Semantics(label: ..., button: true)` node.

## Quality gates

- `flutter analyze lib/models/ad.dart lib/ui/atoms/hatched_painter.dart lib/ui/cards/ad_card.dart test/ui/cards/ad_card_test.dart` → `No issues found!` (0.7 s).
- `flutter test test/ui/cards/ad_card_test.dart` → `+11: All tests passed!`.
- `flutter test test/ui/` → `+39: All tests passed!` — no regressions in P04–P08 atoms/cards.
- No `lib/features/**` imports. No new dependencies. No widget-level business logic.

## Acceptance criteria

- [x] `AdStyle.values == {off, subtle, card, banner}`.
- [x] All three visible variants render without exception at 375-px width.
- [x] Shared «РЕКЛАМА · BRAND» mono label with 5×5 lime dot across `subtle` / `card` / `banner`.
- [x] `subtle`: 4-px lime strip, `NFRadii.brLg`, padding `EdgeInsets.fromLTRB(18, 14, 14, 12)`, title 18/w700/-0.4, desc 13.5/mute, pill CTA.
- [x] `card`: 1.5-px lime border, 88-px hatched header via `CustomPainter`, giant brand wordmark 34/w800/-1.2 in `limeInk` top-right, body title 18/w700/-0.4 + desc 13.5/mute + pill CTA.
- [x] `banner`: 54×54 lime hatched tile with first letter 22/w800, title 15/w700/-0.3/line 1.3 ellipsized, tagline 12.5/mute ellipsized, close 26×26 right.
- [x] Tap close → `onHide(ad)` and NOT `onClick`.
- [x] Tap body → `onClick(ad)`.
- [x] `HatchedPainter` extracted to `lib/ui/atoms/hatched_painter.dart` for P18 reuse.
- [ ] Goldens (`ad_subtle.png`, `ad_card.png`, `ad_banner.png` at 375/760) — deferred, golden infrastructure not yet set up (consistent with P04–P08 deferrals).
