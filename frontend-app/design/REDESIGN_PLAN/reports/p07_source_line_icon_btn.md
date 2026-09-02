# P07 · SourceLine + IconBtn atoms

**Spec:** `design/REDESIGN_PLAN/prompts/07_source_line_icon_btn.md`
**Reference:** `design/reference/mockup/cards.jsx` → `SourceLine`, `IconBtn`

## Files created

- `lib/ui/atoms/source_line.dart`
- `lib/ui/atoms/icon_btn.dart`
- `test/ui/atoms/source_line_icon_btn_test.dart`

## SourceLine

Signature matches spec: `SourceLine({String source, DateTime time, String? readTime, VoidCallback? onMore})`.

Structure:

- 18×18 `NFColors.ink` rounded tile (radius 4), centered `RotatedBox(quarterTurns: 1)` containing a lime `ColoredBox` 10×10 — the placeholder diamond glyph.
- Source name — `fontSize: 13`, `fontWeight: w600`, `letterSpacing: -0.1`, `color: NFColors.ink`. Built from a direct `Text` (not `NFText.meta` — that token defaults to `mute`/13 and would require a color override; direct `TextStyle` keeps the 5-property rule explicit and matches the JSX tokens 1:1).
- 3-px `NFColors.mute2` circular dot separator.
- Time rendered via `NFText.mono(_formatRelative(time))`. `_formatRelative` converts a `DateTime` to `сейчас`/`Xм`/`Xч`/`Xд`. `NFText.mono` uppercases, so the final glyphs are `12М`, `3Ч`, etc.
- If `readTime != null`: extra dot + `NFText.mono(readTime!)`.
- If `onMore != null`: 28×28 circular tap target with `NFIcon('more', size: 16, color: NFColors.mute)`, semantics label `Ещё`.

Wrap behaviour:

- Primary layout is `Wrap(spacing: 8, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center)` — glyph/name/dots/time/read all flow and wrap line-by-line when space is tight. No `Flexible` inside `Wrap` (that combination throws `ParentDataWidget` violation).
- When `onMore` is supplied the widget wraps the `Wrap` in an `Expanded` inside a `Row`, with the more-button pinned on the right — the spec's "flex:1 spacer" pattern.

## IconBtn

Signature: `IconBtn({String iconName, VoidCallback onTap, bool warnDot = false, String? semanticLabel})`.

Structure:

- `SizedBox(38, 38)` → `Stack(clipBehavior: Clip.none)`:
  - Positioned.fill: circular `Container` (`shape: circle`, `color: NFColors.surface`, `border: Border.all(color: NFColors.hairline, width: 1)`) with centered `NFIcon(iconName, size: 17)`.
  - If `warnDot`: `Positioned(top: 7, right: 8, child: _WarnDot())` — 7×7 circle, `NFColors.warn` fill, 1.5-px `NFColors.surface` ring.
- `GestureDetector(behavior: HitTestBehavior.opaque, onTap: onTap)` wraps the whole 38×38 region.
- `Semantics(label: semanticLabel ?? iconName, button: true)` for accessibility.

## Tests (all green, 6/6)

`test/ui/atoms/source_line_icon_btn_test.dart`:

1. `SourceLine renders source name, formatted time, and optional readTime` — asserts `Хабр`, `12М` (mono-uppercased `12м`), `2 МИН ЧТЕНИЯ`.
2. `SourceLine wraps when source name is long (no overflow)` — 240-px viewport, very long source name, no exceptions, `Wrap` exists in the tree.
3. `SourceLine more button renders only when onMore is provided` — `Ещё` semantics missing by default, present when callback supplied, tap increments counter once.
4. `IconBtn renders icon without warn dot by default` — single `Container` (just the circle).
5. `IconBtn renders warn dot when warnDot=true` — two `Container` nodes (circle + dot).
6. `IconBtn onTap fires exactly once per tap`.

## Quality gates

- `flutter analyze lib/ui/atoms/source_line.dart lib/ui/atoms/icon_btn.dart test/ui/atoms/source_line_icon_btn_test.dart` → `No issues found!` (0.7 s).
- `flutter test test/ui/atoms/source_line_icon_btn_test.dart` → `6/6 passed`.
- No `lib/features/**` imports. No new dependencies. No favicon fetching — placeholder glyph only.

## Acceptance criteria

- [x] SourceLine renders source + time + optional readTime.
- [x] Wrap layout prevents overflow with long source names.
- [x] Optional 28×28 `more` button does not affect layout when `onMore` is `null`.
- [x] IconBtn renders 38×38 surface circle with hairline border + 17-px icon.
- [x] IconBtn warn-dot (7×7, warn fill, 1.5-px surface ring) is gated on `warnDot`.
- [ ] Golden tests (`source_line_default.png`, `source_line_with_more.png`, `icon_btn_bell_badge.png`) — deferred, not required by this prompt's test scope.
