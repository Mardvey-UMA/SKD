# Prompt 19 — Bookmarks / likes / dislikes screen

**Phase:** 4 · Screens · **Depends on:** 08, 10, 13
**Agent entry:** `/dev bookmarks likes dislikes screen`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 19

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — `BookmarksScreen` (single component, `kind` prop)

## Target files

- `lib/screens/bookmarks/bookmarks_screen.dart` (new)
- Existing `features/collections/presentation/screens/collections_screen.dart` → передать view-часть в `lib/screens/bookmarks`, если нужно (изучить в research-фазе).

## Task

1. Один компонент, `kind: enum { bookmark, like, dislike }`.
2. Title / copy меняется per kind:
   - `bookmark` → «Сохранённое»
   - `like` → «Понравилось»
   - `dislike` → «Не понравилось»
3. Empty-state с kind-specific `iconName / title / desc` (использовать `EmptyState` из Prompt 10).
4. Иначе — list filtered items через `ShortCard` / `LongCard`. **Без ad-injection здесь.**

## Acceptance criteria (Phase-4 template)

- [ ] Рендерится корректно на mobile / tablet / desktop.
- [ ] Matches `BookmarksScreen`.
- [ ] Existing tests проходят.

## Do NOT

- Не трогать repository `CollectionsRepository` / `ApiCollectionsRepository`.
- Не вводить новые пакеты.
