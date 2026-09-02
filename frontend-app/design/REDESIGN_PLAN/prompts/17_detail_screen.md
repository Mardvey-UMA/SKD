# Prompt 17 — Detail screen + related rail

**Phase:** 4 · Screens · **Depends on:** 08, 13
**Agent entry:** `/dev detail screen`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 17

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — `DetailScreen`, `RelatedRail`

## Target files

- `lib/screens/detail/detail_screen.dart` (new)
- `lib/screens/detail/related_rail.dart` (new)
- `lib/features/feed/presentation/screens/article_detail_screen.dart` — **view-часть мигрирует в `lib/screens/detail`**, business-logic (fetch article, interactions) → sibling controller.

## Task

1. Back-button top-left — pill с `back` icon + «Назад в ленту», padding `8, 14, 8, 10`, hairline border.
2. Article block центрирован, `maxWidth: 720` на tablet+, full width на mobile.
3. Заголовок:
   - desktop: `46/w800/letter -1.6/line 1.05`
   - mobile: `28/w800/letter -1/line 1.1`
4. Hero image 380 tall, `NFRadii.radiusLg`. Пропустить если `item.images == 'none'`.
5. Body: `17/1.7/ink2`, разбиение по `\n\n` на paragraphs.
6. Action bar внизу: `ReactionBar` + pill «Открыть источник» с `external` icon → `url_launcher`.
7. `RelatedRail` — horizontal scrollable rail с 2.5 visible cards. Chip «Смотреть все» → navigation на `RelatedAllScreen` (существующая route).

## Acceptance criteria (Phase-4 template)

- [ ] Рендерится корректно на mobile / tablet / desktop.
- [ ] Matches `DetailScreen` в `screens.jsx`.
- [ ] Existing integration-тесты проходят.
- [ ] OPEN event отправляется при входе в screen.
- [ ] CLOSE event отправляется при выходе (существующее поведение — не менять).

## Do NOT (Phase-4 block)

- Не трогать repository / providers / routing.
- Не вводить новые пакеты.
- Не изменять логику `InteractionAction`.
