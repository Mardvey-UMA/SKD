# Prompt 11 — Bottom nav pill · Report

**Slug:** `redesign-p11-bottom-nav`
**Date:** 2026-04-21
**Commit:** `feat(ui): BottomNav pill (redesign P11)`

## Scope delivered

- `lib/ui/nav/bottom_nav.dart` — pill-shaped bottom navigation with 4 tabs
  (`feed / collections / profile / settings`, Russian labels
  `Лента / Подборки / Профиль / Настройки`).
- `test/ui/nav/bottom_nav_test.dart` — 4 widget tests (render, per-index
  active styling, onTab for inactive, onRetap for active).
- Public API: `BottomNav(activeIndex, onTab, onRetap?)` +
  `BottomNavTab` enum (render order / index map).

## Design adherence

| Requirement                        | Implementation                                           |
|------------------------------------|----------------------------------------------------------|
| Pill container                     | `DecoratedBox` + `BorderRadius.circular(999)`            |
| Background                         | `NFColors.surface`                                       |
| Hairline border                    | `Border.all(color: NFColors.hairline, width: 1)`         |
| Shadow                             | `NFShadows.bottomNav`                                    |
| Inner padding                      | `EdgeInsets.all(6)`                                      |
| 4 Expanded tabs                    | `Row` + `Expanded` × 4 (via indexed loop)                |
| Active bg / icon / label           | `NFColors.ink` / `Color(0xFFFFFFFF)` / `NFColors.lime`   |
| Inactive bg / icon / label         | transparent / `NFColors.ink2` / `NFColors.mute`          |
| Label style                        | 11 px · `FontWeight.w700` · `letterSpacing: 0.1`         |
| Press scale 0.92                   | Inline `AnimatedScale` + `GestureDetector` (no PressScale yet) |
| Outer 12-px offset                 | Parent-owned (delegated to `ResponsiveShell`, P-later)   |
| Backdrop blur                      | **Skipped** — Risk-flag #1 (CanvasKit FPS drop)          |
| `onRetap` reserved prop            | Fired when user taps the already-active index            |

## Non-goals respected

- No 5th tab.
- No Material `BottomNavigationBar` (built on `widgets.dart` only).
- No new dependencies.
- No parent positioning logic (the widget is placement-agnostic).

## Quality gates

| Check            | Result |
|------------------|--------|
| `flutter analyze` (changed files) | clean (0 issues) |
| `flutter test test/ui/nav/bottom_nav_test.dart` | 4/4 green |

## Follow-ups (out of scope)

- Golden tests per active index (tracked in spec acceptance § «Golden на каждый активный индекс»).
- `ResponsiveShell` integration + desktop hiding (upcoming prompt).
- Swap inline `AnimatedScale` for shared `PressScale` wrapper in Prompt 24.
