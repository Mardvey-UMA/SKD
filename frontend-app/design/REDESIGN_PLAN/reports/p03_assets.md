# Redesign P03 — Assets: fonts, icons, stripe placeholder — Result

Slug: `redesign-p3-assets-fonts-icons-placeholder`
Commit: `cfef4e9`
Date: 2026-04-20

## Nunito font — mode: local

- `assets/fonts/Nunito-Variable.ttf` (276 932 B, wght 200..1000) from `github.com/google/fonts/raw/main/ofl/nunito/Nunito%5Bwght%5D.ttf`
- `assets/fonts/Nunito-Italic-Variable.ttf` (281 832 B)
- pubspec font matrix 400..900 + italic 400/700
- `google_fonts` pub dep **удалён**; `GoogleFonts.*` и runtime precache выпилены из `lib/theme/typography.dart` и `lib/main.dart` (обратно синхронный `main()`). `grep -r 'GoogleFonts\|google_fonts' lib/` → 0.

## SVG icon atlas — 27 files

`arrow-right, arrow-up-right, back, bell, bookmark, check, chevron, close, external, eye-off, feed, filter, folder-plus, gear, grid, layers, link, more, plus, radar, search, spark, star, thumb-down, thumb-up, trash, user`

Envelope: `viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'`.
`more.svg` + `radar.svg` заменили `stroke → fill='currentColor' stroke='none'` на filled элементах.

## pubspec.yaml

- `+ flutter_svg: ^2.0.10+1` (единственная новая dep)
- `- google_fonts: ^6.2.1`
- `+ assets/icons/`
- `+ Nunito` family

## Files created

- `lib/ui/atoms/nf_icon.dart` — `NFIcon(name, size:22, color?)` на `SvgPicture.asset` + `ColorFilter.mode(BlendMode.srcIn)`.
- `lib/ui/atoms/stripe_placeholder.dart` — `StripePlaceholder` + `StripeTone` (8: `ink, accent, lime, light, warn, violet, teal, rose`). `CustomPainter` 135° × 14-px + radial highlight + optional label/seed, `ClipRRect(16)`.
- 27 `assets/icons/*.svg`
- 2 `assets/fonts/Nunito*-Variable.ttf`

## Files edited

- `pubspec.yaml`, `pubspec.lock`, `lib/theme/typography.dart`, `lib/main.dart`

## Analyze

- Новые/изменённые: 0 issues.
- Проект: 23 pre-existing в нетронутых файлах (cleanup — P26).

## Sandbox note

Host sandbox блокирует `Write(.claude/artifacts/**)` из sub-claude. Переключил дальнейшие отчёты в `design/REDESIGN_PLAN/reports/`.
