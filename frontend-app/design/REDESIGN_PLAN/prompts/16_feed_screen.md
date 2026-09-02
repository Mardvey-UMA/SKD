# Prompt 16 — Feed screen

**Phase:** 4 · Screens · **Depends on:** 08, 09, 10, 13
**Agent entry:** `/dev feed screen redesign`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 16

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — `FeedScreen`
- `design/reference/mockup/radar-mobile.html` — state wiring
- `design/reference/mockup/data.jsx` — `FEED`, `ADS` schemas

## Target files

- `lib/screens/feed/feed_screen.dart` (new visual screen)
- `lib/screens/feed/feed_header.dart` (new)
- `lib/features/feed/presentation/screens/feed_screen.dart` (edit — указывает на новый `lib/screens/feed/feed_screen.dart` ИЛИ переименовать, обсудить в `/dev` research-фазе)

## Constraint-wall

> **Do NOT change** existing state providers, repositories, routers, API clients, или navigator routes. Only presentation layer. Существующий `FeedScreen` в `features/feed/presentation/screens` содержит business logic (pagination, interaction tracking). Перенос:
> - Business-logic → `lib/features/feed/presentation/controllers/feed_screen_controller.dart` (извлечь 1:1, side-effects не меняем).
> - View → новый `lib/screens/feed/feed_screen.dart`.
> - `FeedScreenController` остаётся Riverpod-провайдером.

## Task

### `FeedHeader` (sticky)

- mobile: padding `EdgeInsets.fromLTRB(18, 62, 18, 14)` (62 покрывает status bar в `DeviceFrame`).
- tablet / desktop: `EdgeInsets.fromLTRB(32, 20, 32, 14)`.
- Лого-mark + `NFText.h2('Радар', w800)` слева.
- Pill «Для вас» (`ink` bg, accentInk fg) справа.

### Feed list

- Item gap 14px.
- Padding:
  - mobile: `EdgeInsets.fromLTRB(14, 4, 14, 120)`
  - tablet: `EdgeInsets.fromLTRB(32, 4, 32, 120)`
  - desktop: `EdgeInsets.fromLTRB(0, 4, 0, 120)` внутри `ConstrainedBox(maxWidth: 760)` по центру.
- `ListView.builder` (не `Column.map`) — performance-rule.

### Ad injection

- Параметры: `adFrequency` (default 5), `adStyle` enum из Prompt 9.
- Каждый N-й элемент — из `ADS` pool, пропуская `hiddenAds` set.
- Никогда не инжектить на позиции 0 или last.
- Никогда два ад-карда подряд.
- Реализовать через `useMemo`-аналог (computed `List<FeedEntry>` в provider, пересчёт только при смене items / hidden / freq).

### Skeleton / empty

- `isLoading` → `FeedSkeleton` (Prompt 10).
- `items.isEmpty && !isLoading` → `EmptyState` с «Пока пусто» + CTA.
- Footer после последней карточки: `NFText.mono('◦ КОНЕЦ ЛЕНТЫ · ПОТЯНИТЕ ДЛЯ ОБНОВЛЕНИЯ ◦')`.

### Sticky header implementation

**Не** использовать `SliverAppBar(pinned: true) + BackdropFilter` — в Risk-flags помечено как jitter на CanvasKit. Используй `CustomScrollView` + `SliverPersistentHeader` без blur; на mobile можно вовсе отказаться от pin и ре-маунтить header при смене таба.

### More-menu

- Тап «more» на card (подведён в Prompt 15) → открыть `CardMenu`.

### Interactions

- IMPRESSION: `VisibilityDetector` на каждую карточку, threshold 0.5, dedup per `feed_request_id` — провайдер уже существует в `features/interactions`, **переиспользуй**.
- LIKE / DISLIKE / BOOKMARK: колбэки `ReactionBar` → existing `InteractionsBatcher` (без изменений).

## Acceptance criteria (Phase-4 template)

- [ ] Рендерится корректно на mobile / tablet / desktop.
- [ ] Совпадает с `FeedScreen` в `screens.jsx`.
- [ ] Scroll-позиция сохраняется при переключении табов (как в существующем поведении).
- [ ] Тап «more» → `CardMenu`.
- [ ] Все существующие integration-тесты (`integration_test/`) проходят без правок.
- [ ] IMPRESSION / LIKE события попадают в batch с тем же `feed_request_id` / `position_in_feed` / `scroll_depth` / `metadata` / `device_type` / `app_version` что и до редизайна (нет регрессий в data-capture).

## Do NOT (Phase-4 block)

- Не трогать state-провайдеры / repositories / `ApiFeedRepository`.
- Не менять `InteractionAction` enum.
- Не вводить новые пакеты.
- Не писать в `SharedPreferences` напрямую — persistence через existing `UserPrefsRepository`.
