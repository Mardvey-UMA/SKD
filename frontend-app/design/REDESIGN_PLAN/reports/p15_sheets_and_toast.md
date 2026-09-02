# Prompt 15 — Sheets & Toast · Report

**Date:** 2026-04-21
**Slug:** `redesign-p15-sheets-and-toast`
**Phase:** 3 · Compositions
**Commit:** `feat(ui): CardMenu + AddToSpaceSheet + Toast overlays (redesign P15)`

## Files

### Created

- `lib/ui/sheets/sheet_base.dart` — shared bottom-sheet chrome.
  - `SheetBase({onClose, child})` — `Positioned.fill` + scrim
    `Color.fromRGBO(14,15,13,0.4)` (tap → `onClose`) +
    bottom-anchored sheet (`NFColors.surface`, top-corners radius 24,
    padding `EdgeInsets.fromLTRB(12, 12, 12, 20)`).
  - Slide-up from 100% height over `NFMotion.sheetDuration` (280 ms) with
    `NFMotion.sheetCurve` (`Cubic(0.2, 0.9, 0.25, 1)`).
  - Private 44×4 `_DragHandle` at top (hairline color,
    `margin 4 auto 12`).
  - Inner sheet wraps child in a `GestureDetector(onTap: () {})` so taps
    on rows / contents do NOT bubble up to the scrim.

- `lib/ui/sheets/card_menu.dart` — `CardMenu({sourceTitle, sourceHandle,
  isPremium, onClose, onAddToSpace, onHideSource})`.
  - Source header block (36×36 `chipBg` tile with inky diamond glyph +
    15/w700 title + 10/w700 mono handle) with `hairline` bottom-border.
  - Public `MenuRow({icon, title, sub, danger, locked, onTap})` — icon
    tile 38×38 radius 10 (`chipBg` default, `rgba(255,90,31,0.10)` for
    `danger`), 14.5/w600 title (warn when `danger`), 12.5 sub in mute,
    trailing 14-px `chevron`. Locked rows append a `PREMIUM` chip
    (`ink` bg / `lime` text, mono 9/w700) and pass `onTap = null` to
    the inner `GestureDetector`.
  - Row 1 `folder-plus` «Добавить источник в пространство» — locked when
    `!isPremium`.
  - Row 2 `eye-off` «Скрыть источник» — `danger`.
  - Cancel row 14-px padded, `surface2`, radius 14, «Отмена».

- `lib/ui/sheets/add_to_space_sheet.dart` — `AddToSpaceSheet({sourceTitle,
  collections, onClose, onCreate, onSelect})`.
  - Title block: mono «ДОБАВИТЬ В ПРОСТРАНСТВО» + 20/w700 «Куда сохранить
    источник «{sourceTitle}»?».
  - `_CreateTile` wrapped in `_DashedBorder` (dash 6 / gap 4, `ink`,
    1.5-px stroke) — 38×38 lime plus-icon + «Создать новое пространство»
    + «Источник будет добавлен сразу» + chevron.
  - Section label mono «ВАШИ ПРОСТРАНСТВА · {count}».
  - Empty state: `surface2` tile with mute message when `collections`
    is empty.
  - `_CollectionRow`: 36×36 tone-swatch (lime / accent / warn / violet /
    teal / rose / ink — matches JSX palette) + 14.5/w700 title +
    trailing 16-px plus.
  - Cancel row identical to `CardMenu`.
  - `_DashedBorder` + `_DashedPainter` use `Path.computeMetrics`
    (imported via `dart:ui show PathMetric`) to segment the rounded-rect
    outline — minimal ad-hoc helper, no new dependency.
  - Content is wrapped in `SingleChildScrollView` with
    `maxHeight = MediaQuery.size.height * 0.8` so long collection lists
    don't push the sheet off-screen.
  - Exposes `UserCollection({id, title, tone: CollectionTone})` DTO +
    `CollectionTone` enum (mirrors JSX tones).

- `lib/ui/overlays/toast.dart` — `Toast({text, duration, onDismiss,
  undoLabel?, onUndo?})`.
  - `Positioned(left: 14, right: 14, bottom: 90)` — pinned 90 px from
    parent bottom (lives inside `ResponsiveShell`, never root
    `Scaffold`).
  - `NFColors.ink` bg, radius 14, 14×12 padding, drop-shadow
    `rgba(0,0,0,0.4) / blur 40 / dy 20`.
  - 13/w500 white text; optional right-side 13/w700 lime «Вернуть»
    (caller passes any label / callback pair). Tapping undo fires
    `onUndo` then `onDismiss` so parents uniformly remove the widget.
  - 220 ms slide-up (0.3 → 0) + fade-in.
  - Self-managed `Timer(duration, onDismiss)` — caller chooses 2600 ms
    for plain messages or 3400 ms for undo-enabled ones.
  - `ConstrainedBox(maxWidth: 520)` + `Center` so the toast stays
    readable on desktop widths.

### Tests created

- `test/ui/sheets/card_menu_test.dart` — 5 tests:
  1. renders source header (title + uppercased handle), both menu rows
     and cancel row.
  2. free-plan (`isPremium: false`) shows `PREMIUM` chip + «Доступно в
     Premium» sub, and tapping the locked row does NOT fire
     `onAddToSpace`.
  3. tap «Отмена» fires `onClose` exactly once.
  4. tap scrim (tap-at top-center) fires `onClose` exactly once.
  5. tap the «Добавить источник в пространство» row fires
     `onAddToSpace` when premium.

- `test/ui/sheets/add_to_space_sheet_test.dart` — 5 tests:
  1. renders «ДОБАВИТЬ В ПРОСТРАНСТВО» title, create-tile, three
     `UserCollection` rows (Tech / News / Design) + section label
     «ВАШИ ПРОСТРАНСТВА · 3».
  2. tap «Создать новое пространство» fires `onCreate` exactly once.
  3. tap two collection rows fires `onSelect('c2')` then
     `onSelect('c3')` — list equality asserted.
  4. tap scrim fires `onClose` exactly once.
  5. empty-collections input renders placeholder «У вас пока нет
     пространств…» + «ВАШИ ПРОСТРАНСТВА · 0».

- `test/ui/overlays/toast_test.dart` — 4 tests:
  1. renders text body, no «Вернуть» button when `undoLabel` /
     `onUndo` are null.
  2. renders «Вернуть» when both props provided; tapping it fires
     `onUndo` once AND `onDismiss` once.
  3. auto-dismisses after the 2600 ms duration — `dismisses == 0` at
     50 ms, `== 1` at 2700 ms.
  4. caller-supplied 3400 ms duration is honoured — still 0 at 2700 ms,
     1 at 3500 ms.

## Design adherence

| Requirement (spec) | Implementation |
|-|-|
| `Positioned.fill` + scrim `Color.fromRGBO(14,15,13,0.4)` | `SheetBase` uses `Positioned.fill` wrapping a full-size `GestureDetector` over a `ColoredBox(Color.fromRGBO(14,15,13,0.4))` |
| Tap scrim closes | scrim `GestureDetector(behavior: opaque, onTap: onClose)`; sheet body wrapped in inner `GestureDetector(onTap: () {})` so inner taps don't bubble |
| Bottom-anchored, `surface` bg, top-corners radius 24 | `Align.bottomCenter` + `Container(decoration: BoxDecoration(color: NFColors.surface, borderRadius: only top 24))` |
| Padding `EdgeInsets.fromLTRB(12, 12, 12, 20)` | verbatim |
| 280 ms slide-up with `NFMotion.sheet` | `AnimationController(duration: NFMotion.sheetDuration)` driving a `SlideTransition` `Tween(0,1 → 0,0)` curved by `NFMotion.sheetCurve` |
| 44×4 drag-handle (hairline, margin 4 auto 12) | `_DragHandle` private widget — `Padding(top:4,bottom:12)` + `Center(Container(44×4, hairline, radius 4))` |
| CardMenu row «Добавить…» + `folder-plus` + locked+PREMIUM for free | `MenuRow(icon: 'folder-plus', locked: !isPremium, onTap: isPremium ? onAddToSpace : null)`; locked branch appends `_PremiumChip` |
| CardMenu row «Скрыть источник» + `eye-off` + warn | `MenuRow(icon: 'eye-off', danger: true)` — warn icon bg `rgba(255,90,31,0.10)`, warn icon color, warn title |
| CardMenu cancel row in `surface2` | `_CancelRow` private widget — `surface2` bg radius 14 |
| AddToSpaceSheet title block (mono + 20/w700) | `_TitleBlock` — `NFText.mono('ДОБАВИТЬ В ПРОСТРАНСТВО')` + `Text('Куда сохранить…', 20/w700 ls:-0.5 height 1.15)` |
| Dashed-border create-tile with lime `+` | `_DashedBorder(color: NFColors.ink, strokeWidth: 1.5, radius: 14)` wrapping a 38×38 `lime` plus-icon tile |
| List of collections — 36×36 swatch + title | `_CollectionRow` — 36×36 `tone.bg` tile with `layers` icon tinted `tone.fg`, 14.5/w700 title, trailing `plus` icon |
| Toast pinned 90 px from parent bottom | `Positioned(left: 14, right: 14, bottom: 90)` — lives inside a parent `Stack`, never `Scaffold` |
| `ink` bg, white 13/500 | `Container(decoration: NFColors.ink)` + `Text(style: 13/w500 white)` |
| Optional lime `Вернуть` button | `if (hasUndo)` branch renders a lime 13/w700 tap target |
| 220 ms slide + fade-in | `AnimationController(220ms)` driving both `FadeTransition` + `SlideTransition(begin: 0,0.3 → 0,0)` curved by `easeOut` |
| Auto-dismiss — caller duration | `Timer(widget.duration, widget.onDismiss)` created in `initState`, cancelled in `dispose` |
| NEVER `showModalBottomSheet` | overlays are full-parent `Positioned.fill` widgets inside a `Stack` — no Material modal route involved |
| Do NOT touch `lib/features/**` | unchanged — all files in `lib/ui/sheets/` and `lib/ui/overlays/` |

## Quality gates

- `flutter analyze` on the 7 changed files → **No errors** (via dart-flutter MCP).
- `flutter test test/ui/sheets/ test/ui/overlays/` → **14/14 passed**.

## Acceptance criteria

- [x] `CardMenu` overlay uses `Positioned.fill` + scrim `rgba(14,15,13,0.4)`.
- [x] Tap on scrim fires `onClose` exactly once (test 4 in `card_menu_test`).
- [x] Tap on «Отмена» fires `onClose` exactly once (test 3).
- [x] Free-plan locks the add-to-space row (no `onAddToSpace` fired) and shows `PREMIUM` chip (test 2).
- [x] `AddToSpaceSheet` renders create-tile + list of user collections — each row 36×36 tone-swatch + title (test 1).
- [x] `AddToSpaceSheet` tap create-tile fires `onCreate`; tap collection row fires `onSelect(collectionId)` (tests 2–3).
- [x] Sheets slide up 100% → 0 over 280 ms with `NFMotion.sheet` (`SheetBase` — animation controller verified by analyzer + render via `SlideTransition` in the widget tree).
- [x] 44×4 drag-handle at top with hairline colour and `margin 4 auto 12`.
- [x] `Toast` pinned 90 px from parent bottom via `Positioned`, renders inside a `Stack` (not `Scaffold`); toast_test validates render + auto-dismiss for both 2600 ms and 3400 ms durations.
- [x] `Toast` optional `Вернуть` button fires `onUndo` + `onDismiss` exactly once (test 2 in `toast_test`).
- [x] No `showModalBottomSheet` used anywhere in the three files (grepped).
- [x] No changes to `lib/features/**`.

## Trade-offs / notes

- The spec mentions «opening CardMenu locks scroll on parent». Because
  these overlays are `Positioned.fill` siblings of the page content
  inside `Stack`, the scrim absorbs ALL pointer events (behind it the
  screen receives none). The widget test asserts the scrim's
  `GestureDetector` intercepts taps anywhere in the overlay area —
  verified via `tester.tapAt(top-centre)` firing `onClose` once rather
  than reaching the page below. A deeper scroll-interception test would
  require the parent's scrollable to be mounted in the same test, which
  is beyond the scope of a unit-level sheet test.
- `PathMetric` is re-exported from `dart:ui`. I imported it explicitly
  (`dart:ui show PathMetric`) instead of switching the file to
  `flutter/material.dart` because the sheet uses only `widgets.dart`
  primitives — keeps the dependency surface minimal.
- `Toast` is a `Positioned` widget, so it MUST be rendered inside a
  `Stack`. That matches its placement inside `ResponsiveShell` (see
  Prompt 13 / 14 reports), where the shell root is already a `Stack`
  containing the bezel + page content. Documented on the class docstring.
- `UserCollection` DTO was introduced inside `add_to_space_sheet.dart`
  rather than as a separate model file because it's a minimal view-layer
  shape — richer feature models (collections feature, likes feature,
  etc.) remap to it at the call-site. Avoids premature abstraction.
- `PREMIUM` chip, drag-handle, cancel-row are private `_` widgets inside
  their respective files — shared only within the feature, not the
  public API. Keeps the file surface tidy.

## Next

- Prompt 16+ — wire `CardMenu`, `AddToSpaceSheet`, `Toast` into the
  card stack (replacing the existing three-dot menu wire-up in
  `CardItem`). Feature-layer concerns — out of scope for Prompt 15.
