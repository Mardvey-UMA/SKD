# Redesign orchestration — Радар Flutter web

Это оркестровка полного редизайна `frontend-app` по макету `design/reference/mockup/` и промптам `design/reference/radar-redesign-prompts.md`.

## Состояние на старт

- AS-IS снэпшот: [`00_as_is.md`](./00_as_is.md)
- Backup tag: [`00_backup.md`](./00_backup.md) → `backup/pre-redesign` = `ea372af`
- 26 self-contained спецификаций: [`prompts/`](./prompts/)

## Как запускать (каждый prompt = одна итерация frontend-app агента)

```bash
cd /home/mattew/SKD/frontend-app

# Запуск prompt N в интерактиве (с user-review-gates):
#   передать содержимое design/REDESIGN_PLAN/prompts/NN_xxx.md как $ARGUMENTS в /dev
claude
> /dev $(cat design/REDESIGN_PLAN/prompts/01_theme_tokens.md)

# Или headless / автономно (НЕ рекомендуется для Phase 4 — там 3 review-gate):
claude -p --dangerously-skip-permissions --output-format json \
       "$(cat design/REDESIGN_PLAN/prompts/01_theme_tokens.md)"
```

Каждый prompt отсылается в `/dev` (или `/fix` для точечных правок). Агент фронта сам читает свой `CLAUDE.md`, `.mcp.json`, skills, и ведёт пайплайн:
`researcher → architect → designer → planner → [implementer + builder]×N → arch-reviewer → reviewer → browser-tester`.

## Порядок исполнения и зависимости

```
Phase 1 · Foundation
  01 theme_tokens                 (none)
  02 responsive_primitives        (← 01)
  03 assets_fonts_icons           (← 01)

Phase 2 · Atoms
  04 text_atoms                   (← 01)
  05 stripe_image_variants        (← 03)
  06 reaction_bar                 (← 01, 03)
  07 source_line_icon_btn         (← 01, 03)
  08 card_shells                  (← 04, 05, 06, 07)
  09 ad_card                      (← 04, 07)
  10 feed_skeleton_empty_state    (← 01, 03)

Phase 3 · Compositions
  11 bottom_nav                   (← 03)
  12 side_nav                     (← 03)
  13 responsive_shell             (← 02, 11, 12)      ← удаляет PhoneAspectRatio
  14 device_frame                 (← 01)              ← раскрывает placeholder 13
  15 sheets_and_toast             (← 01, 03, 04, 07)

Phase 4 · Screens (каждый — Do NOT менять logic / providers / repositories / routes)
  16 feed_screen                  (← 08, 09, 10, 13)
  17 detail_screen                (← 08, 13)
  18 collections_and_editor       (← 13)
  19 bookmarks_likes_dislikes     (← 08, 10, 13)
  20 profile_screen               (← 13)
  21 settings_plan_sources        (← 13, 15)
  22 onboarding_welcome_topics    (← 01, 03, 04)

Phase 5 · Motion
  23 page_transitions             (← 13, 16..22)
  24 press_scale                  (← 06, 11, 12)

Phase 6 · Polish
  25 golden_tests                 (← все 01..24)
  26 regression_sweep_cleanup     (← все 01..25)
```

**Параллелизация** возможна:
- `04` и `05` и `06` и `07` — параллельно после `03`.
- `11` и `12` — параллельно после `03`.
- `16..22` (Phase 4) — можно пускать в разных agent-сессиях ПОСЛЕ 13 + зависимостей, но держать один PR-стрим чтобы не плодить merge-конфликты в `lib/screens/**`.

## Правила миграции (общие для всех prompts)

1. **Логику не трогаем.** Существующие repositories / providers / routers / API clients / `InteractionAction` enum — read-only. Если view и logic в одном файле → extract controller в sibling-файл 1:1.
2. **API-контракт не ломаем.** `API_CONTRACTS.md` — чёрный ящик. Если агент хочет «улучшить» — сначала пишет в research-фазе причину, без одобрения user не меняет.
3. **Data-capture не ломаем.** feed_request_id, position_in_feed, scroll_depth, metadata, device_type, app_version, ab_bucket — продолжают попадать в `/api/interactions/batch` без регрессий.
4. **Новые пакеты**: только `google_fonts` (Prompt 01, временно) → заменяется на local `.ttf` в Prompt 03; `flutter_svg` (Prompt 03). Всё остальное — нет.
5. **Material 3 `ColorScheme`** — выпиливается постепенно. До Prompt 26 старая тема может сосуществовать с `RadarTheme`. В Prompt 26 — финальная чистка.
6. **Persistence** — только через existing `UserPrefsRepository` / `FlutterSecureStorage`. Никакого прямого `SharedPreferences`.
7. **Язык** — UI-текст **русский**, как в `screens.jsx` / `onboarding.jsx`. Commits — English (conventional commits).

## Откат

```bash
cd /home/mattew/SKD/frontend-app

# Безопасно: новая ветка от backup-тега
git checkout -b rollback/pre-redesign backup/pre-redesign

# Точечно: один файл
git checkout backup/pre-redesign -- path/to/file.dart

# Полный откат ветки (destructive!)
git reset --hard backup/pre-redesign
```

## Status tracking

После каждого prompt обновлять таблицу ниже.

| #  | Prompt                             | Status  | Commit(s) | Review gate       |
|----|------------------------------------|---------|-----------|-------------------|
| 01 | theme_tokens                       | pending |           | architecture + UI |
| 02 | responsive_primitives              | pending |           | architecture     |
| 03 | assets_fonts_icons_placeholder     | pending |           | UI               |
| 04 | text_atoms                         | pending |           | UI               |
| 05 | stripe_image_variants              | pending |           | UI               |
| 06 | reaction_bar                       | pending |           | UI               |
| 07 | source_line_icon_btn               | pending |           | UI               |
| 08 | card_shells                        | pending |           | UI               |
| 09 | ad_card                            | pending |           | UI               |
| 10 | feed_skeleton_empty_state          | pending |           | UI               |
| 11 | bottom_nav                         | pending |           | UI               |
| 12 | side_nav                           | pending |           | UI               |
| 13 | responsive_shell                   | pending |           | architecture + UI |
| 14 | device_frame                       | pending |           | UI               |
| 15 | sheets_and_toast                   | pending |           | UI               |
| 16 | feed_screen                        | pending |           | full              |
| 17 | detail_screen                      | pending |           | full              |
| 18 | collections_and_editor             | pending |           | full              |
| 19 | bookmarks_likes_dislikes           | pending |           | full              |
| 20 | profile_screen                     | pending |           | full              |
| 21 | settings_plan_sources              | pending |           | full              |
| 22 | onboarding_welcome_topics          | pending |           | full              |
| 23 | page_transitions                   | pending |           | performance       |
| 24 | press_scale                        | pending |           | code review       |
| 25 | golden_tests                       | pending |           | code review       |
| 26 | regression_sweep_and_cleanup       | pending |           | full              |

## Risk flags (кратко)

См. `design/reference/radar-redesign-prompts.md` § «Флаги риска» — 10 пунктов. Ключевые:
- `backdrop-filter: blur` на sticky-panels — **fallback: solid translucent**.
- SliverAppBar pinned на CanvasKit — **заменить на manual SliverPersistentHeader без pin на mobile**.
- FOUT Nunito — **precache в `main()` до `runApp`**.
- `flutter_svg` raster-cache — **prerendered vector_graphics для nav/reactions, если профилирование покажет drop**.
- `TickerMode + VisibilityDetector` для бесконечных keyframe-анимаций.

## После финального prompt (26)

1. Merge в `main` (если отдельная ветка `feat/radar-redesign` создавалась).
2. Снять snapshot нового SHA в `REDESIGN_PLAN/99_completed.md`.
3. Оставить `backup/pre-redesign` тег навсегда.
