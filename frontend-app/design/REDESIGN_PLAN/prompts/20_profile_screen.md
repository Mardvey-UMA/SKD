# Prompt 20 — Profile screen

**Phase:** 4 · Screens · **Depends on:** 13
**Agent entry:** `/dev profile screen redesign`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 20

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — `ProfileScreen`

## Target files

- `lib/screens/profile/profile_screen.dart` (new)
- Existing `lib/features/profile/presentation/screens/profile_screen.dart` → view-часть мигрирует, logic (ProfileController) в sibling.

## Task

Перенести `ProfileScreen` из JSX дословно:
- Header block: lime-tile avatar (первая буква email), email `24/w800`, mono email-subtitle.
- `PREMIUM` badge (если `isPremium`).
- Rows: bookmarks / likes / dislikes / sources / plan / preferences.
- **Ровно то, что есть в JSX**, ничего не изобретать.

## Acceptance criteria (Phase-4 template)

- [ ] Рендерится на mobile / tablet / desktop.
- [ ] Matches `ProfileScreen`.
- [ ] **Verify explicitly**: нет «stats dashboard», нет «days streak», нет строк, которых нет в `screens.jsx`.
- [ ] Existing tests проходят.

## Do NOT

- Не изобретать новые секции.
- Не трогать `ApiProfileRepository` / logout-flow.
