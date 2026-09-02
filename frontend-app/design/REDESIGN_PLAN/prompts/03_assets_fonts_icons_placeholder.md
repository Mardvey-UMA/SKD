# Prompt 03 — Assets: fonts, icons, stripe placeholder

**Phase:** 1 · Foundation · **Depends on:** 01
**Agent entry:** `/dev assets fonts icons placeholder`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 3

## Context

Поставляем ассеты: Nunito `.ttf` локально (замена runtime-fetch из Prompt 01), SVG-иконки из `Icon` атласа (`tokens.jsx`), `StripePlaceholder` как `CustomPainter`.

## Reference files (read-only)

- `design/reference/mockup/tokens.jsx` — `Icon()` switch (26 имён) + `Stripe` компонент (диагональный 135° паттерн 14/14 + radial highlight)
- `design/reference/radar-redesign-prompts.md` § Prompt 3

## Target files (create new)

- `assets/fonts/Nunito-{Regular,Medium,SemiBold,Bold,ExtraBold,Black}.ttf` — добавить файлы (400/500/600/700/800/900)
- `assets/icons/*.svg` — по одной на каждое имя из `Icon()` switch (24×24 viewBox, stroke-width 1.6, `currentColor`). Имена: `search, bell, thumb-up, thumb-down, bookmark, feed, layers, user, gear, back, plus, chevron, arrow-up-right, arrow-right, external, spark, close, check, filter, grid, trash, link, radar, star, more, eye-off, folder-plus`.
- `pubspec.yaml` — регистрация 6 weights Nunito + `assets/icons/`, добавить `flutter_svg: ^2.0.9` как **единственную** новую dep. Убрать runtime-fetch `google_fonts` для Nunito (FontLoader `rootBundle` → заменить использование `GoogleFonts.nunito()` на `TextStyle(fontFamily: 'Nunito')`).
- `lib/ui/atoms/nf_icon.dart` — `NFIcon(name, size = 22, color?)` → `SvgPicture.asset('assets/icons/$name.svg', colorFilter: ColorFilter.mode(color, BlendMode.srcIn))`.
- `lib/ui/atoms/stripe_placeholder.dart` — `CustomPainter`:
  - `tile = 14px`, угол 135° (= `canvas.rotate(135 * π/180)`)
  - 2 цвета из `tone: StripeTone` enum: `ink, accent, lime, light, warn, violet, teal, rose` (8 значений, палитра — из `Stripe` JSX)
  - radial-highlight overlay: `RadialGradient(center: Alignment(-0.8,-1), radius: 1.2, colors: [white14, transparent])`
  - необязательные `label` (mono, top-left) + `seed` (mono `#NNN`, bottom-right)
  - рендер внутри `ClipRRect(BorderRadius.circular(16))`.

## Web → Flutter mapping

| CSS                                                                        | Flutter                                            |
|----------------------------------------------------------------------------|----------------------------------------------------|
| `repeating-linear-gradient(135deg, A 0 14px, B 14px 28px)`                 | `CustomPainter` с `canvas.rotate`                  |
| `radial-gradient(120% 80% at 10% 0%, white 0%, transparent 60%)`           | `RadialGradient` overlay                           |
| `border-radius: 16px; overflow: hidden`                                    | `ClipRRect(borderRadius: BorderRadius.circular(16))` |

## Acceptance criteria

- [ ] `NFIcon(name: 'gear')` пиксельно совпадает со `gear` в `tokens.jsx`.
- [ ] `StripePlaceholder(tone: StripeTone.lime, label: 'ФОТО', seed: 42)` визуально совпадает с `<Stripe tone="lime" seed={42}/>`.
- [ ] В `pubspec.yaml` `flutter_svg` — единственная новая dep; шесть weights Nunito зарегистрированы локально.
- [ ] `GoogleFonts` больше не дергается runtime (grep: `GoogleFonts.` в `lib/theme/**` отсутствует).
- [ ] Smoke-прогон в Chrome: кеш `.ttf` загружается из bundle, FOUT отсутствует.

## Do NOT

- Не растеризовать иконки в PNG.
- Не рисовать иконки в `CustomPaint` руками — только SVG в `assets/icons/`.
- Не менять логические файлы.
- Не превышать 1 новую dep.
