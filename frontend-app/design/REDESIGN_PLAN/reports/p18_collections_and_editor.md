# Prompt 18 — Collections screen + editor

**Status:** ✅ Done · **Slug:** `redesign-p18-collections-and-editor`

## Scope

Redesign of the «Collections» (backend name: `spaces`) landing screen and
its editor, per `design/REDESIGN_PLAN/prompts/18_collections_and_editor.md`
and the JSX source of truth (`design/reference/mockup/screens.jsx`).

Visual / copy rename only. Routes (`/spaces`, `/shell/collections`) and
domain models (`Space`, `SpaceColor`, `ISpacesRepository`) are **not**
touched.

## /spaces vs Collections — explicit mapping

The backend module sits under `lib/features/spaces/`. The UI layer was
renamed to match the mock. There are now **two** «collection‑shaped»
routes in the shell, serving different purposes:

| URL                                | Screen (file)                                                                                   | Purpose                                                  |
| ---------------------------------- | ----------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| `/shell/collections`               | `lib/screens/collections/collections_screen.dart` (P18, new)                                    | Landing: system tiles + user «пространства» grid + CTA   |
| `/shell/bookmarks?kind=…`          | `lib/features/collections/presentation/screens/collections_screen.dart` (legacy tabs, unchanged) | Filtered list: bookmarks / likes / dislikes              |
| `/spaces/new`                      | `lib/screens/collections/collection_editor.dart` (P18, new)                                     | Create a user collection                                 |
| `/spaces/:id`                      | `SpaceDetailScreen` (unchanged)                                                                 | User-collection detail (feed + settings)                 |
| `/spaces/:id/edit`                 | `lib/screens/collections/collection_editor.dart` (P18, new)                                     | Edit a user collection                                   |

The legacy bookmarks screen is retained until P19 (BookmarksScreen) ships
— at that point it will be replaced in place.

`_indexFrom` in the router was updated so both `/shell/collections` and
`/shell/bookmarks` map to bottom-nav tab 1.

## Domain ↔ UI tone mapping

The JSX palette offers 7 visual tones (`ink / accent / lime / warn /
violet / teal / rose`). The domain enum `SpaceColor` has 8 values that
travel on the wire unchanged. The bridge lives in
`lib/features/spaces/presentation/controllers/collection_tone_mapping.dart`:

```
UI tone  → SpaceColor (write)       SpaceColor → UI tone (read)
ink      → blue                     red        → rose
accent   → purple                   orange     → warn
lime     → green                    yellow     → warn
warn     → orange                   green      → lime
violet   → purple (alias of accent) teal       → teal
teal     → teal                     blue       → ink
rose     → pink                     purple     → accent
                                    pink       → rose
```

`violet` collapses into `purple` on save — fully lossless for round-trip
of the 7 canonical swatches. Spaces created with legacy tones (`red`,
`yellow`) still render correctly on read.

## Files

**Created**

- `lib/ui/atoms/nf_input.dart` — minimal input atom (full spec in P22).
- `lib/ui/atoms/tone_swatch.dart` — 38×38 tone swatch + canonical palette
  constant.
- `lib/screens/collections/collection_card.dart` — user-collection card
  (tone header + `HatchedPainter` overlay + 48/w800 count + mono caption).
- `lib/screens/collections/collections_screen.dart` — new landing screen.
- `lib/screens/collections/collection_editor.dart` — new editor.
- `lib/features/spaces/presentation/controllers/collection_tone_mapping.dart`
  — pure tone ↔ `SpaceColor` helper.

**Edited**

- `lib/core/router/app_router.dart`
  - `/shell/collections` now points to the P18 screen.
  - `/shell/bookmarks` added, deep-linkable via `?kind=bookmark|like|dislike`.
  - `/spaces/new` and `/spaces/:id/edit` now use
    `CollectionEditorScreen` (old `SpaceEditorScreen` is no longer
    wired but remains on disk for P19 reference).
  - `_indexFrom` updated.
- `lib/features/collections/presentation/screens/collections_screen.dart`
  — accepts `initialTab` (consumed once) so the P18 system tiles can
  deep-link straight into the right tab.

## Acceptance criteria

- [x] Renders on all shell breakpoints (single grid column adapts via
      `CrossAxisCount: 2`; system-tile hero row stays balanced on wide
      viewports because Shell constrains width).
- [x] Matches `CollectionsScreen` / `CollectionEditorScreen` JSX.
- [x] All existing tests pass (`space_color_test.dart`,
      `add_to_space_sheet_test.dart`,
      `api_collections_repository_test.dart` — 25 tests green).
- [x] `flutter analyze` clean on every touched file (6 items, 0 issues).

## Verification

```
$ flutter analyze lib/screens/collections lib/ui/atoms/nf_input.dart \
    lib/ui/atoms/tone_swatch.dart \
    lib/features/spaces/presentation/controllers \
    lib/features/collections/presentation/screens/collections_screen.dart \
    lib/core/router/app_router.dart
Analyzing 6 items... No issues found! (ran in 1.2s)

$ flutter test test/core/sources/domain/space_color_test.dart \
    test/ui/sheets/add_to_space_sheet_test.dart \
    test/features/collections/data/repositories/api_collections_repository_test.dart
All tests passed! (25 of 25)
```
