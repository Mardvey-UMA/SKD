# Prompt 21 — Settings + Plan + Sources + Add source + My sources

**Phase:** 4 · Screens · **Depends on:** 13, 15
**Agent entry:** `/dev settings plan sources screens`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 21

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — `SettingsScreen`, `PlanScreen`, `SourcesScreen`, `AddSourceScreen`, `MySourcesScreen`

## Target files

- `lib/screens/settings/settings_screen.dart`
- `lib/screens/settings/plan_screen.dart`
- `lib/screens/settings/sources_screen.dart`
- `lib/screens/settings/add_source_screen.dart`
- `lib/screens/settings/my_sources_screen.dart`
- `lib/ui/gates/plan_gate.dart` — централизованный `isPremium` check

## Task

1. Порт каждого экрана дословно из JSX.
2. Для free-users тап на premium-gated options → routing на `PlanScreen`. Проверка `isPremium` **только через `PlanGate`** (не дублировать).
3. Маппинг в существующие routes:
   - `SettingsScreen` → `/settings`
   - `PlanScreen` → `/subscription` (existing route, не переименовывать)
   - `SourcesScreen` → `/sources-catalog`
   - `AddSourceScreen` → `/add-source`
   - `MySourcesScreen` → `/my-additions` / `/blocked-sources` (изучить в research-фазе какой лучше мапится; возможно MySources — это объединение My additions и Blocked).

## Acceptance criteria (Phase-4 template)

- [ ] Каждый экран рендерится корректно на mobile / tablet / desktop.
- [ ] Matches соответствующему JSX.
- [ ] Existing tests проходят.
- [ ] YooKassa payment flow в `/subscription` не ломается (существующий `ApiSubscriptionRepository`).

## Do NOT

- Не дублировать `isPremium` check.
- Не трогать payment-integration.
- Не переименовывать существующие routes.
