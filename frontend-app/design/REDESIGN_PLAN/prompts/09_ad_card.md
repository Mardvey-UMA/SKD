# Prompt 09 — Ad card: subtle / card / banner

**Phase:** 2 · Atoms · **Depends on:** 04, 07
**Agent entry:** `/dev ad card variants`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 9

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `AdCard`

## Target files (create new)

- `lib/models/ad.dart` — `Ad(brand, tagline, title, desc, cta)`
- `lib/ui/cards/ad_card.dart`

## Task

`enum AdStyle { off, subtle, card, banner }`. Все три варианта показывают «РЕКЛАМА · BRAND» mono-лейбл с 5×5 lime-дотом.

### `subtle`

- Обычная карточка с 4px lime полосой слева.
- `borderRadius: NFRadii.radiusLg`, padding `EdgeInsets.fromLTRB(18, 14, 14, 12)`.
- Title 18/w700/-0.4, desc 13.5 `mute`, pill CTA «→».

### `card`

- Border 1.5px `lime`.
- Header 88px, lime, **hatched 135° pattern** через `CustomPainter` (имеет смысл вынести в `lib/ui/atoms/hatched_painter.dart`).
- Giant brand wordmark 34/w800/-1.2 в `limeInk` top-right.
- Body: title 18/w700/-0.4 + `mute` desc 13.5 + pill CTA.

### `banner`

- 1 row, 54×54 lime hatched-квадрат с первой буквой 22/w800.
- Title 15/w700/-0.3/line 1.3, ellipsized.
- Tagline 12.5 `mute`, ellipsized.
- Close 26×26 справа.

### Callbacks

- `onClick(ad)` — вся карточка кроме close.
- `onHide(ad)` — close-кнопка, `stopPropagation`.

## Acceptance criteria

- [ ] Goldens `ad_subtle.png`, `ad_card.png`, `ad_banner.png` на 375 и 760.
- [ ] `AdStyle.values` == `{off, subtle, card, banner}`.
- [ ] Тап close вызывает `onHide` и НЕ вызывает `onClick`.

## Do NOT

- Не добавлять новые варианты рекламы.
- Не трогать logic / interactions pipeline.
