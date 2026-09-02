# Prompt 25 — Golden tests across breakpoints

**Phase:** 6 · Polish · **Depends on:** Phases 1–5 (Prompts 01–24)
**Agent entry:** `/test architecture` + `/dev golden tests`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 25

## Target files (create new)

- `test/goldens/` — один файл на screen + один файл на atom-state-matrix
- `test/fixtures/mockup_seed.dart` — port `FEED`, `ADS`, `INTERESTS` дословно из `design/reference/mockup/data.jsx`

## Task

1. Для каждого screen из Phase 4 (16–22) — goldens на 3 breakpoints:
   - mobile 375×812
   - tablet 900×1024
   - desktop 1440×900
2. Для каждого atom/composition c несколькими состояниями (Prompts 6, 8, 9, 10, 11, 12, 15) — state-matrix golden.
3. Seed data дословно из `data.jsx` (`FEED`, `ADS`, `INTERESTS`).
4. Render settings: `Brightness.light`, `textScaleFactor = 1.0`, `platformBrightness = light`.
5. CI: добавить job в `.github/workflows/` — `flutter test --reporter=expanded`.

## Acceptance criteria

- [ ] `flutter test --update-goldens` → `flutter test` zero diffs.
- [ ] CI passes.

## Do NOT

- Не коммитить `--update-goldens` без визуального ревью (см. README redesign-плана).
