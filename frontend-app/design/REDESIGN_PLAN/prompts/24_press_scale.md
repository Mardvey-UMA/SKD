# Prompt 24 — PressScale micro-interaction

**Phase:** 5 · Motion · **Depends on:** 06, 11, 12
**Agent entry:** `/dev press scale micro`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 24

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `.tab-anim` CSS class

## Target files

- `lib/ui/motion/press_scale.dart`
- Edit места, где в prompts 06/11/12 был inline `AnimatedScale` → заменить на `PressScale` helper.

## Task

1. `PressScale({double scale = 0.92, Duration duration = const Duration(milliseconds: 180), required Widget child, required VoidCallback onTap})`.
2. Имплементация через `GestureDetector(onTapDown / onTapUp / onTapCancel)` + `AnimatedScale(scale: _pressed ? scale : 1.0)`.
3. Применяется везде, где в JSX есть класс `.tab-anim`:
   - `BottomNav` tabs (Prompt 11)
   - `SideNav` tabs (Prompt 12)
   - `ReactionBar` buttons (Prompt 6)
   - Все pill CTAs («Читать →», «Читать далее →», «Новое пространство», «Продолжить»)

## Acceptance criteria

- [ ] `flutter test` — unit-test, что `onTap` вызывается ровно 1 раз.
- [ ] Все места использования inline-scale заменены на `PressScale` (grep `AnimatedScale` — 0 вхождений в `lib/ui/**`).
- [ ] Документировано: на элементах с `InkWell` ripple — решение (заменить ripple на scale ИЛИ оставить оба).

## Do NOT

- Не вводить `flutter_hooks`.
- Не менять существующие `GestureDetector` в `lib/features/**`.
