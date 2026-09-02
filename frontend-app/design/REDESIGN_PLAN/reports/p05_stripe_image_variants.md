# P05 — Stripe image variants (SingleImage + MultiImage)

**Slug:** redesign-p5-stripe-image-variants
**Spec:** `design/REDESIGN_PLAN/prompts/05_stripe_image_variants.md`
**Reference:** `design/reference/mockup/cards.jsx` → `SingleImage` (L78), `MultiImage` (L82)

## Files created

- `lib/ui/atoms/image_item.dart` — `ImageItem` model (tone, toneSecondary?, toneTertiary?, seed).
- `lib/ui/atoms/single_image.dart` — `SingleImage({item, height = 200})`.
- `lib/ui/atoms/multi_image.dart` — `MultiImage({item, height = 200})` (3-cell Row/Column layout).
- `test/ui/atoms/multi_image_test.dart` — layout-assertion widget tests.

## Implementation notes

- `SingleImage` wraps `StripePlaceholder(tone, seed, label: 'ФОТО', radius: 0)` in a `ClipRRect(16)` so the outer radius is applied exactly once at the atom boundary (avoids nested-clip cost inside `StripePlaceholder`).
- `MultiImage` structure mirrors the JSX grid (`2fr 1fr` + 2 rows) via a single outer `ClipRRect(16)` over a `SizedBox(height)` with:
  - `Row` → `Expanded(flex: 2, left cell)` + `SizedBox(width: 4)` + `Expanded(flex: 1, Column)`.
  - Inner `Column` → `Expanded(top)` + `SizedBox(height: 4)` + `Expanded(bottom)`.
- Each cell uses `StripePlaceholder(radius: 0)` — the outer `ClipRRect` alone rounds the composite.
- Labels `1/3` / `2/3` / `3/3`, seeds `seed` / `seed+10` / `seed+20`, tones `[item.tone, toneSecondary ?? StripeTone.accent, toneTertiary ?? StripeTone.lime]`. The JSX `'accent'` / `'lime'` string keys map to the existing `StripeTone` enum members.
- No `GridView` (per Do-NOT rule), no new dependencies, no changes under `lib/features/**`.

## Item model decision

No pre-existing `item`/card-model type in `lib/**` yet (features are currently pre-redesign). A minimal typed class `ImageItem` was introduced at `lib/ui/atoms/image_item.dart` — scope kept to what the two atoms need (tone + 2 optional tones + seed).

## Verification

- `flutter analyze lib/ui/atoms/{single_image,multi_image,image_item}.dart test/ui/atoms/multi_image_test.dart` → **No issues found**.
- `flutter test test/ui/atoms/multi_image_test.dart` → **All tests passed** (2/2):
  - 390×200 parent: 3 `StripePlaceholder` cells rendered, labels `1/3`/`2/3`/`3/3`, seeds `42`/`52`/`62`, equal right-column halves, left cell taller and wider than right.
  - 720×200 fluid parent: same invariants hold; `left + 4 + right == 720`.

## Out of scope (deferred)

- Golden files (`multi_image_default.png` / `multi_image_wide.png`) — acceptance checks listed in spec but not requested in this execution; layout tests cover structural invariants.
