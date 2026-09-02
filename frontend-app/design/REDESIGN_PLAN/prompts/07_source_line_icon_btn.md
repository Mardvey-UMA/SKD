# Prompt 07 — Source line + Icon button

**Phase:** 2 · Atoms · **Depends on:** 01, 03
**Agent entry:** `/dev source line icon btn`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 7

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `SourceLine`, `IconBtn`

## Target files (create new)

- `lib/ui/atoms/source_line.dart`
- `lib/ui/atoms/icon_btn.dart`

## Task

### `SourceLine({String source, DateTime time, String? readTime, VoidCallback? onMore})`

- 18×18 `ink` квадрат с повёрнутым `lime` ромбом-глифом (`RotatedBox(quarterTurns:1, child: ColoredBox(color: lime, SizedBox(10,10)))`).
- Название источника — 13/w600, `letterSpacing: -0.1`, `ink`.
- 3-px серый dot-separator (`mute2`).
- Время (mono).
- Опциональный read-time (mono после ещё одного dot).
- Опциональная 3-dot `more` кнопка справа (28×28 круг).

### `IconBtn({String iconName, VoidCallback onTap, bool warnDot = false})`

- 38×38 круг, hairline border, `surface` bg.
- Иконка через `NFIcon(name: iconName, size: 17)`.
- `warnDot`: 7×7 оранжевый круг справа-сверху, 1.5px surface-ring.

### Wrap behaviour

`SourceLine` должен корректно переноситься (используй `Wrap` / `Flexible`) если имя источника длинное.

## Acceptance criteria

- [ ] Golden `source_line_default.png`, `source_line_with_more.png`, `icon_btn_bell_badge.png`.
- [ ] Тап на `more` не ломает layout (`onMore` опционален).

## Do NOT

- Не подтягивать фавиконы источников — ромб-глиф это плейсхолдер.
- Не трогать логику.
