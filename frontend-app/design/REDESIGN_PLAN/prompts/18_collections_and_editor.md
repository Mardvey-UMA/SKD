# Prompt 18 — Collections screen + editor

**Phase:** 4 · Screens · **Depends on:** 13
**Agent entry:** `/dev collections screen editor`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 18

## Context

Backend-модуль называется `spaces` (см. `lib/features/spaces/`), но в дизайне — `Collections`. **Roots не переименовываем** (route `/spaces`, domain-модели `SpaceXxx`) — только UI-слой и копирайтинг. В UI label «Подборки / Коллекции» — по тексту из `screens.jsx`.

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — `CollectionsScreen`, `CollectionEditorScreen`

## Target files

- `lib/screens/collections/collections_screen.dart` (new)
- `lib/screens/collections/collection_editor.dart` (new)
- `lib/screens/collections/collection_card.dart` (new)
- Existing `lib/features/spaces/presentation/screens/{spaces_screen,space_editor_screen}.dart` → перенести view-часть в `lib/screens/collections/*` (миграция 1:1), business logic в sibling controller.

## Task

### `CollectionsScreen`

3 секции:
1. **Системные**: Сохранённое / Понравилось / Не понравилось (как system-тайлы).
2. **Ваши**: user collections карточки.
3. CTA «Новое пространство» внизу (primary pill).

### User collection card

- Header 120 tall, tone-colored, с hatched overlay (переиспользуй `HatchedPainter` из Prompt 9).
- Giant count number `48/w800` bottom-right.
- Title `17/w700` снизу.
- Mono `{count} МАТЕРИАЛОВ · {sources} ИСТОЧНИКОВ`.

### Navigation

- Тап на card → `CollectionEditorScreen`.
- Тап на system-tile → filtered `BookmarksScreen` (Prompt 19) с соотв. `kind`.

### `CollectionEditorScreen`

- Title input.
- Desc input.
- Tone picker (7 swatches: `ink / accent / lime / warn / violet / teal / rose`).
- Sources picker (multi-select из `SYSTEM_SOURCES` + `customSources`).
- Save / Delete action-rows.

## Acceptance criteria (Phase-4 template)

- [ ] Рендерится корректно на всех breakpoints.
- [ ] Matches `CollectionsScreen` / `CollectionEditorScreen`.
- [ ] Existing tests проходят.

## Do NOT

- Не переименовывать route `/spaces`.
- Не менять `SpaceRepository` / `SpacesProvider`.
- Не вводить новые пакеты.
