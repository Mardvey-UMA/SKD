# Prompt 26 — Functional regression sweep + legacy cleanup

**Phase:** 6 · Polish · **Depends on:** Prompts 01–25
**Agent entry:** `/test full` затем `/dev cleanup legacy theme widgets`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 26

## Цели

1. Прогон всех existing integration-тестов и manual-smoke.
2. Удаление legacy-файлов, которые пережили миграцию (мы оставляли их жить на время редизайна).

## Reference files (read-only)

- `design/reference/mockup/screens.jsx` — финальный визуальный таргет
- `API_CONTRACTS.md` — контракт (нельзя ломать)

## Target actions

### A. Regression sweep

Прогнать `integration_test/` целиком. Для каждого failure — классифицировать:
- (a) presentation-only regression → починить новый widget 1:1 к прежнему поведению;
- (b) accidental logic change → откатить файл до `backup/pre-redesign` (git) и перепортировать только view.

### B. Manual smoke script

```
login → onboarding (3-5 topics) → feed scroll →
open detail → bookmark → back → open bookmarks → unbookmark →
switch tabs (feed ↔ collections ↔ profile ↔ settings)
```

Запустить в Chrome (Playwright MCP), снять скриншоты финальных состояний.

### C. Legacy cleanup (аккуратно!)

Удалить **только** если нет импортов из `lib/features/**`:
- `lib/theme/app_theme.dart` (старая Material-3 тема) — если `RadarTheme.light` полностью заменил.
- `lib/theme/app_tokens.dart` + `lib/theme/tokens/*` — если все использования переехали на `NFColors/NFRadii/NFTypography/...`.
- `lib/widgets/action_icon_button.dart`, `brand_pill.dart`, `destructive_button.dart`, `feed_card.dart`, `sticky_glass_header.dart` — если заменены на `lib/ui/**` эквиваленты.
- `makets/*.png` — legacy мокапы, `design/reference/mockup/` теперь source of truth. **Перенести в git-tag комментарии**, потом удалить каталог.
- `PhoneAspectRatio` в `lib/app.dart` (должен быть удалён уже в Prompt 13 — тут финальная проверка).

### D. pubspec clean

Убрать `google_fonts` если он больше нигде не используется (Nunito едет через local assets из Prompt 03).

## Acceptance criteria

- [ ] Все pre-existing integration-тесты проходят без правок.
- [ ] Smoke-script проходит green без exceptions в console.
- [ ] `grep -r 'import.*app_theme.dart'` — 0.
- [ ] `grep -r 'import.*lib/widgets/'` — 0 (или остались только те, что перенесены в `lib/ui/`).
- [ ] `grep -r 'GoogleFonts'` в `lib/` — 0.
- [ ] `flutter analyze` — 0 warnings.
- [ ] `flutter build web --release` — success; в bundle нет device-frame assets.
- [ ] `flutter build apk --debug` — success (Android target работоспособен).

## Do NOT

- Не удалять файлы из `lib/features/*/data/` или `lib/features/*/domain/`.
- Не удалять `API_CONTRACTS.md`.
- Не ломать payment-flow `/subscription`.
- Не удалять backup-тег `backup/pre-redesign`.
