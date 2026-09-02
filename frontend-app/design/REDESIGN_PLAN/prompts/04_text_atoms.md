# Prompt 04 — Text atoms: NFText + Mono

**Phase:** 2 · Atoms · **Depends on:** 01
**Agent entry:** `/dev nf text atoms`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 4

## Reference files (read-only)

- `design/reference/mockup/tokens.jsx` — `Mono` component
- `design/reference/mockup/cards.jsx` — использование в `SourceLine`, `CardBody`, `AdCard`

## Target files (create new)

- `lib/ui/atoms/nf_text.dart`

## Task

1. `NFText.display / h1 / h2 / body / meta / mono` — named constructors над `Text`, используют `NFTypography` из Prompt 01.
2. `NFText.mono(String value)` — **uppercase** значение, `letterSpacing: 0.8`, `fontSize: 10`, `FontWeight.w700`, `color: NFColors.mute` (перекрываемый).
3. Пропси: `textAlign`, `maxLines`, `overflow`, необязательный `color`.
4. `display` принимает опциональный `breakpoint` (или резолвить через `context.breakpoint`) — 38/42/46 и letter -1.2/-1.4/-1.6.

## Acceptance criteria

- [ ] Golden `nf_text_display.png` на mobile / tablet / desktop совпадает со снимком.
- [ ] `NFText.mono('РЕКЛАМА · BRAND')` рендерится uppercase без `.toUpperCase()` в call-site.
- [ ] `flutter analyze` чисто.

## Do NOT

- Не делать inline-`TextStyle` в call-site — всё через `NFText`.
- Не трогать логические файлы.
