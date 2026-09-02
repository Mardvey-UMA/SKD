# Prompt 06 — Reaction bar (like / dislike / bookmark)

**Phase:** 2 · Atoms · **Depends on:** 01, 03
**Agent entry:** `/dev reaction bar atom`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 6

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `ReactionBar`

## Target files (create new)

- `lib/ui/atoms/reaction_bar.dart`

## Task

1. Три круглые кнопки 40×40 (compact 34×34), spacing 8.
2. **Like** — active: fill `NFColors.accent`, icon `thumb-up` в `accentInk`.
3. **Dislike** — active: fill `NFColors.ink`, icon белый.
4. **Bookmark** — active: fill `NFColors.lime`, icon `bookmark` (stroke + fill `limeInk`).
5. Inactive для всех: transparent fill, hairline border, icon `ink2`.
6. Колбэки `onLike / onDislike / onBookmark`. **Атом stateless** — переключение like/dislike (взаимоисключение) лежит на родителе.
7. Пресс-анимация: scale → 0.92 на press-down, `NFMotion.fast` (180ms, `Curves.easeOut`).
8. `GestureDetector(behavior: HitTestBehavior.opaque)` + не всплывать дальше (чтобы тап на реакции не триггерил `onOpen` карточки).

## Web → Flutter mapping

| CSS                                            | Flutter                                                |
|------------------------------------------------|--------------------------------------------------------|
| `transition: background 160ms ease, border-color 160ms ease` | `AnimatedContainer(duration: NFMotion.fast)`       |
| `e.stopPropagation()`                          | `HitTestBehavior.opaque` + не пробрасывать ontap вверх |

## Acceptance criteria

- [ ] 8 golden-ов на матрицу состояний (like on/off × dislike on/off × bookmark on/off, исключая взаимоисключенные сочетания).
- [ ] Тап на `like` при активном `dislike` **не** снимает dislike внутри атома.
- [ ] При изменении prop `isLiked` — плавный переход 180ms.

## Do NOT

- Не держать внутреннее состояние — атом stateless.
- Не импортировать `lib/features/**` — атом не знает про InteractionAction.
