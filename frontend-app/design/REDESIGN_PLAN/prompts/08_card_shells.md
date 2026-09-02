# Prompt 08 — Card shells: ShortCard, LongCard

**Phase:** 2 · Atoms · **Depends on:** 04, 05, 06, 07
**Agent entry:** `/dev card shells short long`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 8

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `ShortCard`, `LongCard`, `CardBody`

## Target files (create new)

- `lib/ui/cards/card_body.dart`
- `lib/ui/cards/short_card.dart`
- `lib/ui/cards/long_card.dart`

## Task

### Общая оболочка

- `surface` bg, `borderRadius: NFRadii.radiusLg` (26), hairline border, `NFShadows.card`.
- `item.images ∈ {one, multi, none}`. `none` — без image-блока и без header padding.
- Image-padding: 8 со всех сторон.
- Body-padding: `EdgeInsets.fromLTRB(18, 14, 18, 14)`.
- Body: `SourceLine`, заголовок, snippet, `ReactionBar` + inky pill «Читать →».

### `ShortCard`

- Title: 21 / w600 / letter -0.5 / line 1.15 / `ink`.
- Snippet: 14.5 / 1.5 / `mute`, без clamp.
- CTA: «Читать →».

### `LongCard`

- Title: 22 / w700 / letter -0.6 / line 1.14.
- Snippet: clamp `maxHeight: 96` + 40px white-fade overlay снизу.
- CTA: «Читать далее →».

### Callbacks

- `onOpen(item)` — тап по `title` и по CTA.
- `onReact(item, action)` — от `ReactionBar`.
- Тап на реакции **не** должен пробрасывать `onOpen` (см. Prompt 06 `HitTestBehavior.opaque`).

### Fade overlay mapping

CSS `linear-gradient(to bottom, rgba(255,255,255,0), surface)` → `Positioned.fill` + `DecoratedBox(LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [transparent, NFColors.surface]))` внутри `SizedBox(height: 40)` на `Align(bottomCenter)`.

## Acceptance criteria

- [ ] Goldens: `short_card_one.png`, `short_card_multi.png`, `short_card_none.png`, `long_card.png` на ширине 375 и 760.
- [ ] Тап по title / «Читать» вызывает `onOpen` ровно 1 раз.
- [ ] Тап по реакции не вызывает `onOpen`.

## Do NOT

- Не подгружать реальные изображения — только `StripePlaceholder`.
- Не трогать `lib/features/feed/presentation/widgets/*_card_widget.dart` — это legacy; они будут заменены на `ShortCard/LongCard` в Prompt 16.
