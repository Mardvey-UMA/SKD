# Prompt 02 — Responsive primitives

**Phase:** 1 · Foundation · **Depends on:** 01
**Agent entry:** `/dev responsive primitives`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 2

## Context

Закладываем брейкпоинты и помощники. Макет fluid — одна дерева, меняется только внешний shell. На фронте сейчас всё в `PhoneAspectRatio` (lib/app.dart) — **не трогать** в этом prompt; `ResponsiveShell` подключается в Prompt 13.

## Reference files (read-only)

- `design/reference/mockup/radar-web.html` — `useLayout()`, `ResponsiveShell`
- `design/reference/mockup/README.md` — таблица breakpoints
- `design/reference/radar-redesign-prompts.md` § Breakpoints

## Target files (create new)

- `lib/responsive/breakpoint.dart`
- `lib/responsive/responsive_builder.dart`
- `lib/responsive/responsive_value.dart`
- `lib/responsive/context_ext.dart`

## Task

1. `enum Breakpoint { mobile, tablet, desktop }`.
2. Пороги: `mobileMax = 767`, `tabletMax = 1199` — `static const`.
3. `extension BreakpointContext on BuildContext`:
   - `Breakpoint get breakpoint` (from `MediaQuery.sizeOf(this).width`)
   - `bool get isMobile / isTablet / isDesktop`
4. `class ResponsiveValue<T> { final T mobile; final T? tablet; final T? desktop; T resolve(BuildContext); }`:
   - `tablet` fallbacks to `mobile`.
   - `desktop` fallbacks to `tablet` (далее к `mobile`).
5. `class ResponsiveBuilder extends StatelessWidget` — оборачивает `LayoutBuilder`, колбэк `(ctx, Breakpoint)`. Документировать: использовать когда вложенный constraint важнее window size.

## Web → Flutter mapping

| CSS                               | Dart                                          |
|-----------------------------------|-----------------------------------------------|
| `@media (min-width: 1200px)`      | `context.breakpoint == Breakpoint.desktop`    |
| `useLayout()` hook                | `context.breakpoint` extension                |

## Acceptance criteria

- [ ] Unit-test: `ResponsiveValue(mobile: 10, desktop: 20).resolve(ctx)` → 10 / 10 / 20 (при ширинах 375 / 900 / 1440).
- [ ] Widget-test: `ResponsiveBuilder` возвращает правильный breakpoint на 375 / 900 / 1440.
- [ ] `flutter analyze` чисто.

## Do NOT

- Не добавлять `flutter_screenutil` — шкала абсолютная.
- Не трогать `lib/main.dart` / `lib/app.dart`.
- Не трогать `lib/features/**`.
