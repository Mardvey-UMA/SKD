# AS-IS snapshot — frontend-app до полного редизайна

Дата фиксации: **2026-04-20**
Git HEAD на момент снэпшота: `ea372af658ac0fdac0b83e33a1018e17ad60ce80`
Ветка: `master`
Бекап-тег (см. `00_backup.md`): `backup/pre-redesign`

---

## 1. Стек и архитектура

| Слой            | Что есть                                                                          |
|-----------------|-----------------------------------------------------------------------------------|
| Framework       | Flutter 3.x / Dart 3, SDK `^3.11.1`                                               |
| State           | `flutter_riverpod ^2.4.0` + `riverpod_annotation`                                 |
| Network         | `dio ^5.4.0` (реальные ApiXxxRepository против `api-gateway :8080`)               |
| Routing         | `go_router ^13.0.0`                                                               |
| Storage         | `flutter_secure_storage` (JWT), `shared_preferences` (user prefs — предположительно) |
| UI deps         | `cached_network_image`, `smooth_page_indicator`, `flutter_widget_from_html_core`, `visibility_detector`, `url_launcher` |
| NEW в plan      | `google_fonts` (Nunito), `flutter_svg` (иконки) — добавляются в Prompt 1 / 3      |

Архитектура уже Clean: `lib/features/{feature}/{data,domain,presentation}`.
215 `.dart` файлов суммарно.

## 2. Существующие экраны (22 `_screen.dart`)

```
features/
  auth/                  login, registration, email_verification_pending/code,
                         forgot_password, reset_password, change_password
  onboarding/            topic_selection_screen
  feed/                  feed_screen, article_detail_screen, related_list_screen
  collections/           collections_screen
  spaces/                spaces_screen, space_detail_screen, space_editor_screen
  profile/               profile_screen
  settings/              settings_screen
  subscription/          subscription_screen
  add_source/            add_source_screen
  blocked_sources/       blocked_sources_screen
  sources_catalog/       sources_catalog_screen
  my_additions/          my_additions_screen
  device_info/           device_info_screen
  shell/                 main_shell_screen
  interactions/          (no screens, providers/services)
```

## 3. Текущий UI-kit и тема

- `lib/theme/app_theme.dart` — Material 3 `ColorScheme` (ЦЕЛЬ redesign: выпилить M3-scheme в пользу явных токенов `NF`)
- `lib/theme/app_tokens.dart`, `lib/theme/theme_context_extension.dart`
- `lib/theme/tokens/app_{colors,elevation,gradients,motion,radii,spacing,typography}.dart`
- `lib/widgets/{action_icon_button,brand_pill,destructive_button,feed_card,sticky_glass_header}.dart` — точечные, **без design-system из нового мокапа**
- Мокапы `/makets/*.png` — старый стиль (до редизайна), должны быть заменены на `design/reference/mockup/`

## 4. Entry-точка и шелл

- `lib/main.dart` → `ProviderScope(App())`.
- `lib/app.dart` → `MaterialApp.router` + `PhoneAspectRatio` wrapper (фикс. 390×844). Это **временный** dev-frame, в редизайне — заменить на `ResponsiveShell` + `DeviceFrame` из Prompt 13–14.
- Одиночная dart-ветка без адаптивной оболочки mobile/tablet/desktop — будет введена в Prompt 2 / 13.

## 5. Интеграция с бэком (актуально, не трогать в редизайне)

- Все repositories — реальные `ApiXxx` против `http://localhost:8080`.
- Контракты: `frontend-app/API_CONTRACTS.md` (39 KB, актуальны на 2026-04-20).
- Canonical `InteractionAction` enum (6 значений — IMPRESSION/OPEN/CLOSE/LIKE/DISLIKE/BOOKMARK) — уже мигрирован. Redesign **не меняет** enum, только UI-представление.
- Auth: JWT access 15 мин / refresh 30 дней через `AuthTokenInterceptor` + `RefreshTokenInterceptor`.
- Data-capture (feed_request_id, position_in_feed, scroll_depth, metadata) — работает; redesign обязан сохранить эти поля при обработке тапов.

## 6. Что уже untracked на момент снэпшота

```
design/    # свеже-скопированные референсы (mockup/ + radar-redesign-prompts.md)
```

Будут закоммичены вместе с planом (`design/REDESIGN_PLAN/*`).

## 7. Ключевые зоны риска при редизайне

| # | Риск                                                                      | Где всплывёт      |
|---|---------------------------------------------------------------------------|-------------------|
| 1 | Material 3 `ColorScheme` в `app_theme.dart` конфликтует с явными `NF`     | Prompt 1          |
| 2 | `PhoneAspectRatio` в `app.dart` ломает будущий `ResponsiveShell`          | Prompt 13         |
| 3 | Кастомные widgets в `lib/widgets/*.dart` дублируют будущие `NFIcon/IconBtn/ReactionBar` | Prompt 6–7        |
| 4 | `lib/features/spaces/` = будущие Collections; имена не совпадают с mockup (`CollectionsScreen`) | Prompt 18         |
| 5 | `makets/*.png` — legacy, не совпадают с `design/reference/mockup/`        | все Phase 4       |
| 6 | `google_fonts` runtime-fetch → FOUT на web; precache в `main()`           | Prompt 1, 3       |
| 7 | `flutter_svg` перерастеризация 26 иконок на CanvasKit — нужен кеш        | Prompt 3, 6, 11   |
| 8 | SliverAppBar pinned + BackdropFilter на CanvasKit = jitter                | Prompt 16         |

## 8. Правило-охранник на весь редизайн

> **Логику не трогаем.** Переписываются только:
> `lib/theme/**`, `lib/ui/**` (новая папка), `lib/screens/**` (новая папка-зеркало), `lib/responsive/**` (новая папка), `assets/**`.
>
> Существующие `lib/features/{feature}/data/` и `/domain/` (repositories, providers, DTOs, routers, API clients) — **read-only**.
> Если экран смешивает логику с представлением, извлечь логику в sibling-controller **без изменения side-effects**.

Полный свод правил — в `design/reference/radar-redesign-prompts.md` → блок Phase 4 Do NOT.
