# Prompt 05 — Stripe placeholder variants: single + multi

**Phase:** 2 · Atoms · **Depends on:** 03
**Agent entry:** `/dev stripe image variants`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 5

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `SingleImage`, `MultiImage`

## Target files (create new)

- `lib/ui/atoms/single_image.dart`
- `lib/ui/atoms/multi_image.dart`

## Task

1. `SingleImage({item, height = 200})` — `ClipRRect(radius 16)` + `StripePlaceholder(tone: item.tone, seed: item.seed, label: 'ФОТО')`.
2. `MultiImage({item, height = 200})` — 3 cell layout:
   - `Row(children: [Expanded(flex: 2, left), Expanded(flex: 1, Column)])`
   - Left cell: одна большая tile.
   - Right column: две `Expanded(flex: 1)` tile.
   - Gap 4 px (`SizedBox(width: 4)` / `SizedBox(height: 4)`).
   - Внешний radius 16, внутренние — 0.
   - `tones = [item.tone, item.toneSecondary ?? accent, item.toneTertiary ?? lime]`.
   - Лейблы `1/3`, `2/3`, `3/3`; seeds `seed`, `seed+10`, `seed+20`.

## Acceptance criteria

- [ ] Golden `multi_image_default.png` на 390×200 pixel-match с мокапом.
- [ ] Golden `multi_image_wide.png` на 720×200 (fluid parent).

## Do NOT

- Не использовать `GridView` — layout фиксированный 3-cell.
- Не трогать логические файлы.
