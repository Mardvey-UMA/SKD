# Prompt 12 — Side nav (desktop)

**Phase:** 3 · Compositions · **Depends on:** 03
**Agent entry:** `/dev side nav desktop`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 12

## Reference files (read-only)

- `design/reference/mockup/radar-web.html` — `SideNav`

## Target files (create new)

- `lib/ui/nav/side_nav.dart`

## Task

1. `width: 240`, padding `EdgeInsets.symmetric(vertical: 28, horizontal: 16)`, `bg: NFColors.bg`, hairline справа.
2. Не скроллится сам (скролл — в main column). Имплементация: просто `Column` в desktop-ветке shell.
3. Header: 32×32 logo-mark (diamond-with-ring SVG, то же что в mobile) + `NFText.h2('Радар', weight: w800, letter: -0.8)`. Padding `0, 6, 20, 6`.
4. 4 таба (те же, что в `BottomNav`).
5. Active: `ink` bg, white fg.
6. Inactive: `ink` fg, transparent bg.
7. Hover: 140ms ease bg transition (через `MouseRegion` + `AnimatedContainer`).
8. Press: scale 0.92 (inline `AnimatedScale`).

## Acceptance criteria

- [ ] Golden на desktop 1440 — logo + tabs pixel-match с `radar-web.html`.
- [ ] Смена активного таба не ребилдит sibling-виджеты (provider-scoped).

## Do NOT

- Не добавлять хоткеи, badges, user-card — только spec-parity.
- Не скроллить сам sidebar.
