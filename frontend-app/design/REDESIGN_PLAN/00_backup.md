# Backup point — pre-redesign

Фиксация состояния frontend-app **до** полного редизайна по `design/reference/radar-redesign-prompts.md`.

## Git coordinates

| Свойство        | Значение                                    |
|-----------------|---------------------------------------------|
| Repository      | `frontend-app/.git` (standalone, own repo)  |
| Branch          | `master`                                    |
| HEAD SHA        | `ea372af658ac0fdac0b83e33a1018e17ad60ce80`  |
| Short SHA       | `ea372af`                                   |
| HEAD subject    | `merge feat/mvp-hardening: canonical InteractionAction + 4 api repositories + docs` |
| Snapshot date   | 2026-04-20                                  |
| Backup tag      | `backup/pre-redesign`                       |

## Как откатиться

```bash
cd /home/mattew/SKD/frontend-app

# Просмотреть тег
git show backup/pre-redesign --stat

# Полный откат ветки (деструктивно, потеряет все redesign-коммиты)
git reset --hard backup/pre-redesign

# Безопасная альтернатива: новая ветка с тега
git checkout -b rollback/pre-redesign backup/pre-redesign
```

## Что закоммичено вместе с этим снэпшотом

- `design/reference/mockup/`  — источник правды редизайна (tokens.jsx, cards.jsx, screens.jsx, onboarding.jsx, radar-web.html, device frames, data.jsx, README.md).
- `design/reference/radar-redesign-prompts.md` — 26 промптов в 6 фазах (Foundation → Atoms → Compositions → Screens → Motion → Polish).
- `design/REDESIGN_PLAN/` — orchestration artefacts (этот файл, AS-IS, per-prompt spec-ы, README с порядком запуска).

## Untouched (защищено redesign-правилом)

- `lib/features/*/data/` — все `ApiXxxRepository`
- `lib/features/*/domain/` — все providers / use-cases / models / interfaces
- `lib/core/` — router, config, errors, validators, services
- `API_CONTRACTS.md` — контракт с бэкендом
- `pubspec.yaml` меняется **только** в Prompt 1 (google_fonts) и Prompt 3 (flutter_svg + fonts/icons регистрация)
