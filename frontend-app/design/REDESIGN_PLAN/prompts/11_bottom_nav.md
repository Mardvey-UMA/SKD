# Prompt 11 — Bottom nav pill (mobile + tablet)

**Phase:** 3 · Compositions · **Depends on:** 03
**Agent entry:** `/dev bottom nav pill`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 11

## Reference files (read-only)

- `design/reference/mockup/radar-web.html` — `BottomNav`

## Target files (create new)

- `lib/ui/nav/bottom_nav.dart`

## Task

1. 4 таба: `feed / collections / profile / settings`. Labels: `Лента / Подборки / Профиль / Настройки`.
2. Pill-container:
   - 12px от left / right / bottom родителя (его поставит `ResponsiveShell`).
   - `borderRadius: BorderRadius.circular(999)`.
   - `surface` bg, hairline border.
   - `NFShadows.bottomNav`.
   - inner padding 6 по всем сторонам.
   - `Row` из 4 `Expanded`-таба.
3. Active tab: `ink` bg, icon белый, label `lime` w700 11px.
4. Inactive: transparent bg, icon `ink2`, label `mute` w700 11px.
5. Тап-анимация: scale → 0.92 на press-down (см. будущий `PressScale` Prompt 24; в этом prompt — inline `AnimatedScale`).
6. **Без `backdrop-filter: blur`** — на CanvasKit FPS-drop, в Risk-flags помечено.

## Web → Flutter mapping

| CSS                        | Flutter                                                      |
|----------------------------|--------------------------------------------------------------|
| `box-shadow: 0 12px 30px -10px rgba(0,0,0,0.18)` | `NFShadows.bottomNav`                                        |
| `border-radius: 999px`     | `BorderRadius.circular(999)`                                 |

## Acceptance criteria

- [ ] Golden на каждый активный индекс (4 изображения).
- [ ] Виден на mobile / tablet, скрыт на desktop (логика скрытия в `ResponsiveShell`).
- [ ] При тапе по уже-активному табу — scroll-to-top callback (reserved prop `onRetap`).

## Do NOT

- Не добавлять 5-й таб.
- Не использовать `BottomNavigationBar` из Material.
