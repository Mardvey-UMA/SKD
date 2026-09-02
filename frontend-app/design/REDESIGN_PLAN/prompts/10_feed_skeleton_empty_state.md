# Prompt 10 — Feed skeleton + Empty state

**Phase:** 2 · Atoms · **Depends on:** 01, 03
**Agent entry:** `/dev feed skeleton empty state`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 10

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `FeedSkeleton`, `EmptyState`

## Target files (create new)

- `lib/ui/atoms/feed_skeleton.dart`
- `lib/ui/atoms/empty_state.dart`

## Task

### `FeedSkeleton`

- 3 placeholder-карточки с shimmer.
- Shimmer: gradient sweep `-400 → 400` px, 1400ms линейно infinite. Реализуй через **один** `AnimationController` на весь виджет + `ShaderMask` (а не отдельный контроллер на каждую карточку).
- Header: lime pulse-dot (scale 1 → 1.6 → 1 за 1300ms ease-in-out) + `NFText.mono('ВАША ЛЕНТА ФОРМИРУЕТСЯ...')`.
- `TickerMode` автоматически останавливает контроллер при уходе виджета из viewport — оборачивай в `VisibilityDetector` и ставь `TickerMode(enabled: visible)`.

### `EmptyState({String iconName, String title, String desc, Color accent, Widget? action})`

- Outer card.
- Две concentric dashed rings:
  - Наружная: rotation 16s
  - Внутренняя: rotation 10s в обратную сторону
  - Рисуй через `CustomPainter` + `strokeDasharray`-эквивалент (Flutter stroke dashed — ручной loop).
- Центр 86×86 disk в `accent`, floating ±4px за 3s.
- `NFIcon` size 22 по центру.
- Title 18/w700/-0.3.
- Desc 13.5/1.5 `mute`.
- Опциональный primary pill action.

## Acceptance criteria

- [ ] При уходе из viewport анимация **останавливается** (проверить через `TickerMode.of(context).muted == true`).
- [ ] Static-frame-t=0 goldens для обоих виджетов.
- [ ] Нет `shimmer` пакета — всё вручную.

## Do NOT

- Не использовать `shimmer` dep.
- Не создавать отдельный `AnimationController` на каждую skeleton-карточку — один shared.
