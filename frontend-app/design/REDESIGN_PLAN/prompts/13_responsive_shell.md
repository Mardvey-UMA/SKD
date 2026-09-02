# Prompt 13 — Responsive shell

**Phase:** 3 · Compositions · **Depends on:** 02, 11, 12
**Agent entry:** `/dev responsive shell`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 13

## Context

Сейчас `lib/app.dart` оборачивает дерево в `PhoneAspectRatio` (фикс. 390×844). **Этот prompt удаляет `PhoneAspectRatio`** и заменяет на `ResponsiveShell`.

## Reference files (read-only)

- `design/reference/mockup/radar-web.html` — `ResponsiveShell` + mobile/tablet/desktop ветки

## Target files (create / edit)

- `lib/ui/shell/responsive_shell.dart` (create)
- `lib/ui/shell/device_frame.dart` (create — placeholder для Prompt 14)
- `lib/app.dart` (edit — удалить `PhoneAspectRatio` wrapper, обернуть `router` → `ResponsiveShell`)

## Task

### `ResponsiveShell({Widget child, Breakpoint bp, int activeTab, ValueChanged<int> onTab, bool showBottomNav = true})`

- **mobile** (`< 768`): центрируем `child` в `DeviceFrame` (из Prompt 14; сейчас плейсхолдер-виджет с такими же dim-ами). 28 vertical / 16 horizontal padding. `BottomNav` оверлеем на абсолют. позиции 12px от edges контейнера.
- **tablet** (`768–1199`): `Center` → `ConstrainedBox(maxWidth: 520)` → rounded `surface` панель (`NFRadii.circular(28)`, hairline border, `NFShadows.tabletPanel`) + внутри child + `BottomNav` оверлеем.
- **desktop** (`≥ 1200`): `Row([SideNav, Expanded(child)])`. Без `BottomNav`.
- `showBottomNav` уважается только на mobile/tablet.

### Интеграция с go_router

Так как `app.dart` использует `MaterialApp.router`, обернуть **builder** `routerConfig.routerDelegate` → `ResponsiveShell` можно не напрямую, а через `ShellRoute` в `app_router.dart`:
- В `lib/core/router/app_router.dart`: добавить `ShellRoute(builder: (ctx, state, child) => ResponsiveShell(child: child, bp: ctx.breakpoint, activeTab: _indexFrom(state.uri), onTab: _handleTab))`.
- `_indexFrom` маппит `/feed → 0`, `/collections → 1`, `/profile → 2`, `/settings → 3`.
- `onTab` — `context.go('/feed'|...)`.
- **Не менять существующие route paths** — только обернуть.

## Acceptance criteria

- [ ] Widget-test на каждый breakpoint: 375, 900, 1440 → правильный shell.
- [ ] Тап на `SideNav` таб (desktop) = тап на `BottomNav` таб (mobile): одинаковый state-переход.
- [ ] `CardMenu` (Prompt 15) оверлеится корректно поверх shell во всех breakpoints.
- [ ] Переходя из 375 в 1440 в Chrome DevTools Responsive — layout обновляется без перезапуска app.
- [ ] Существующие тесты (`flutter test`) проходят без правок.

## Do NOT

- Не использовать `Scaffold(bottomNavigationBar: ...)` — nav оверлеем.
- Не трогать существующие route paths / redirect logic.
- Не удалять код существующего `main_shell_screen.dart` — если есть конфликт, `ResponsiveShell` **заменяет** его визуально, но логику (auth-guard, deep-link) оставить.
