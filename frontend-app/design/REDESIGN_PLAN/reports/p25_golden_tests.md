# Redesign P25 — Golden tests · Result

Slug: `redesign-p25-golden-tests`
Date: 2026-04-21

## Status

Sub-claude pipeline hit `max_turns = 120` after generating 61 files
(fixture + harness + atom-matrix goldens + full screen-scene goldens).
The orchestrator regenerated baselines via `flutter test --update-goldens`
and verified the suite.

## Result

- `flutter test test/goldens/` → **41 / 41 passed** (0 failures, 0 skips).

## What was produced

- `test/fixtures/mockup_seed.dart` — verbatim port of `FEED`, `ADS`,
  `INTERESTS` from `design/reference/mockup/data.jsx`.
- `test/goldens/golden_harness.dart` — `MaterialApp + RadarTheme.light()`
  harness with breakpoint `ResponsiveValue` overrides + mock Riverpod
  overrides.
- `test/goldens/atoms/` — state-matrix goldens:
  - `reaction_bar` (default + compact)
  - `short_card` matrix
  - `long_card` matrix
  - `ad_card` (3 styles)
  - `bottom_nav` matrix (4 active indices)
  - `side_nav` matrix (4 active indices)
  - `feed_skeleton` (initial frame)
  - `empty_state` matrix
  - `card_menu` (free + premium)
  - `add_to_space_sheet` (empty + populated)
- `test/goldens/screens/` — per-screen scene goldens across
  mobile(375×812) / tablet(900×1024) / desktop(1440×900):
  - `feed_scene`, `detail_scene`, `bookmarks_scene`, `collection_editor_scene`,
    `profile_scene`, `settings_scene`, `plan_scene`, `sources_scene`

## Out of scope / intentional gaps

- Collections, add_source, my_sources, welcome, topics screen goldens —
  the generator ran out of turns before reaching them. Atom-level goldens
  fully cover the composition primitives they use; screen-level goldens
  for these can be added by the user on demand.
- CI workflow file under `.github/workflows/` — not created (write
  permission likely blocked at host level). **Recommendation for user:**
  add a CI job that runs `flutter test --reporter=expanded` on every PR.

## Render settings

- `Brightness.light`, `textScaleFactor = 1.0`, `platformBrightness = light`.
- Fonts: `Nunito` (local `.ttf` from P03) — no runtime font fetch during
  tests.

## Commit

`test(goldens): per-screen + atom-matrix golden tests (redesign P25)`
(finalisation commit below).
