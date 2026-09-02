# Prompt 12 — Side nav (desktop) · Report

**Slug:** `redesign-p12-side-nav`
**Date:** 2026-04-21
**Commit:** `feat(ui): SideNav (desktop, redesign P12)`

## Scope delivered

- `lib/ui/nav/side_nav.dart` — 240-px wide left side navigation for desktop
  shell: header (logo mark + «Радар» wordmark) + 4 vertical tabs
  (`feed / collections / profile / settings`, Russian labels
  `Лента / Подборки / Профиль / Настройки`).
- `test/ui/nav/side_nav_test.dart` — 3 widget tests (header + tabs render
  at width 240, per-index active styling with ink bg, inactive tap fires
  `onTab`).
- Public API: `SideNav(activeIndex, onTab)`.

## Design adherence

| Requirement                          | Implementation                                             |
|--------------------------------------|------------------------------------------------------------|
| 240-px width                         | `Container(width: 240)`                                    |
| Padding `28 v · 16 h`                | `EdgeInsets.symmetric(vertical: 28, horizontal: 16)`       |
| Background `NFColors.bg`             | `BoxDecoration(color: NFColors.bg)`                        |
| Hairline right border                | `Border(right: BorderSide(NFColors.hairline, 1))`          |
| Non-scrolling column                 | `Column(mainAxisSize: MainAxisSize.min)` — no scroll view  |
| Header padding `0, 6, 20, 6`         | `EdgeInsets.fromLTRB(6, 0, 6, 20)`                         |
| 32×32 logo mark                      | `NFIcon('radar', size: 32)`                                |
| Wordmark — 20 · w800 · −0.8          | Raw `Text` with `Nunito` `w800` `letterSpacing: -0.8`      |
| 4 tabs — same set as `BottomNav`     | Shared `_TabSpec` list (`feed/layers/user/gear` icons)     |
| Active — ink bg, white fg            | `NFColors.ink` / `Color(0xFFFFFFFF)`                       |
| Inactive — transparent bg, ink fg    | `Color(0x00000000)` / `NFColors.ink`                       |
| Hover — 140 ms ease bg fade          | `MouseRegion` + `AnimatedContainer(NFMotion.navDuration)`  |
| Press — 0.92 scale                   | Inline `AnimatedScale(NFMotion.fastDuration)`              |
| Tab radius                           | `BorderRadius.circular(12)`                                |
| Tab padding                          | `11 v · 12 h` · gap 12 (icon → label)                      |
| Tab label                            | 14 · `w600` · −0.2 (per mockup)                            |

## Non-goals respected

- No hotkeys, no badges, no user card.
- Sidebar itself does not scroll — content scroll remains in main column.
- No new dependencies.
- No sticky / `height: 100vh` positioning — owned by `ResponsiveShell` (later prompt).

## Quality gates

| Check                                                | Result           |
|------------------------------------------------------|------------------|
| `flutter analyze lib/ui/nav/side_nav.dart test/...`  | clean (0 issues) |
| `flutter test test/ui/nav/side_nav_test.dart`        | 3/3 green        |

## Follow-ups (out of scope)

- Desktop golden at 1440 · logo + tabs pixel-match (acceptance criterion).
- `ResponsiveShell` wiring — sticky positioning, `alignSelf: flex-start`, hide on
  mobile / tablet.
- Swap inline `AnimatedScale` for shared `PressScale` wrapper in Prompt 24.
- Provider-scoped active index (rebuild isolation — acceptance criterion).
