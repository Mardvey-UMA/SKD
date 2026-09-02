# Prompt 01 — Theme tokens

**Phase:** 1 · Foundation · **Depends on:** —
**Agent entry:** `cd frontend-app && claude` → `/dev theme tokens`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 1

## Context

Flutter-web проект `frontend-app` уже существует. Закладываем design-токены из нео-футуристического макета `radar-web`. Логику не трогаем. Material-3 `ColorScheme` в `lib/theme/app_theme.dart` **уступает место** явным токенам `NF` — старая тема остаётся жить только на время миграции, finalize удаление — в Prompt 26.

## Reference files (read-only)

- `design/reference/mockup/tokens.jsx` — `NF` палитра, `FONT_SANS`/`FONT_MONO`
- `design/reference/mockup/README.md`
- `design/reference/radar-redesign-prompts.md` § «Design tokens (reference table)»

## Target files (create new)

- `lib/theme/colors.dart`
- `lib/theme/typography.dart`
- `lib/theme/radii.dart`
- `lib/theme/motion.dart`
- `lib/theme/shadows.dart`
- `lib/theme/radar_theme.dart` — composer `ThemeData`
- `lib/main.dart` — wire `RadarTheme.light` into `MaterialApp.theme` (уже импортирован `app.dart`; делаем так, чтобы `app.dart` использовал `RadarTheme.light()` вместо `buildLightTheme()`).

## Task

1. `NFColors` — mirror всей таблицы токенов:
   - `bg #F4F5F2`, `surface #FFFFFF`, `surface2 #ECEDE8`
   - `hairline Color.fromRGBO(17,17,17,0.09)`
   - `ink #0E0F0D`, `ink2 #2A2B28`, `mute #6E6F6A`, `mute2 #9A9B96`
   - `accent #3B2BFF`, `accentInk #FFFFFF`
   - `lime #CCFF33`, `limeInk #0E0F0D`
   - `warn #FF5A1F`, `chipBg #E8E9E3`
2. `NFRadii` — `radius = 18`, `radiusLg = 26`, `radiusSm = 10` как `BorderRadius`.
3. `NFMotion` — четыре именованные пары `(Duration, Curve)`:
   - `fast` = 180ms + `Curves.easeOut`
   - `base` = 240ms + `Curves.easeOutCubic`
   - `sheet` = 280ms + `Cubic(0.2, 0.9, 0.25, 1)`
   - `nav` = 140ms + `Curves.easeOut`
4. `NFShadows.card / bottomNav / toast / tabletPanel` → `List<BoxShadow>` 1-в-1 с CSS (negative spread преобразуй).
5. `NFTypography` (Nunito через **`google_fonts`** — единственная новая dep в этом prompt):
   - `display` 38/42/46, w800, letter-spacing -1.2/-1.4/-1.6 (responsive: резолв идёт в widget-слое, в стиле — базовое 42/-1.4)
   - `h1` 22, w700, -0.6
   - `h2` 21, w600, -0.5
   - `body` 14.5, height 1.5
   - `meta` 13, w600
   - `mono` 10, w700, uppercase (делается на widget-уровне), letterSpacing 0.8, tabular figures
6. `RadarTheme.light` — собрать `ThemeData(useMaterial3: false` ИЛИ включенный, но **без** `ColorScheme.fromSeed`; токены — явные). Без dark-темы.
7. Pre-cache шрифтов: `GoogleFonts.config.allowRuntimeFetching` оставить true для dev, но в `main()` вызвать `GoogleFonts.pendingFonts([...])` или `FontLoader` для weights 400/500/600/700/800/900 **до** `runApp` → убирает FOUT.

## Web → Flutter mapping

| CSS                            | Dart                                                    |
|--------------------------------|---------------------------------------------------------|
| `rgba(17,17,17,0.09)`          | `Color.fromRGBO(17, 17, 17, 0.09)`                      |
| `letter-spacing: 1.2px`        | `letterSpacing: 1.2`                                    |
| `text-transform: uppercase`    | `.toUpperCase()` в widget-слое (документировать выбор)  |
| `font-variation: wght N`       | `FontWeight.w{N}`                                       |

## Acceptance criteria

- [ ] `flutter analyze` чисто.
- [ ] `flutter run -d chrome --web-port=8080` стартует; фон экрана `#F4F5F2`.
- [ ] Smoke-widget `Text('Радар', style: NFTypography.display)` рендерится Nunito w800 42px.
- [ ] Каждый токен таблицы имеет Dart-константу; никаких magic numbers.
- [ ] Старый `buildLightTheme()` больше не вызывается в `app.dart`.

## Do NOT

- Не удалять `lib/theme/app_theme.dart` / `app_tokens.dart` / `tokens/*` в этом prompt — только переставить на `RadarTheme.light`. Удаление — в Prompt 26.
- Не добавлять state-management пакеты.
- Не трогать файлы в `lib/features/*/data` и `lib/features/*/domain`.
- Не добавлять Material 3 seed generation.
