# Prompt 15 — Sheets: CardMenu, AddToSpaceSheet, Toast

**Phase:** 3 · Compositions · **Depends on:** 01, 03, 04, 07
**Agent entry:** `/dev sheets card menu add to space toast`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 15

## Reference files (read-only)

- `design/reference/mockup/cards.jsx` — `CardMenu`, `AddToSpaceSheet`, `MenuRow`
- `design/reference/mockup/radar-web.html` — toast in `App`

## Target files (create new)

- `lib/ui/sheets/card_menu.dart`
- `lib/ui/sheets/add_to_space_sheet.dart`
- `lib/ui/overlays/toast.dart`

## Task

1. Все overlay используют `Positioned.fill` + scrim `Color.fromRGBO(14,15,13,0.4)`. Тап на scrim → close.
2. Sheet:
   - bottom-anchored, `surface` bg, верхние углы radius 24.
   - padding `EdgeInsets.fromLTRB(12, 12, 12, 20)`.
   - slide-up от 100% высоты за 280ms `NFMotion.sheet`.
   - drag-handle 44×4 наверху (hairline color, margin `4 auto 12`).
3. `CardMenu`:
   - Row «Добавить источник в пространство» — `folder-plus` icon, для free-plan → locked + `PREMIUM` chip (берётся из `PlanGate`, Prompt 21).
   - Row «Скрыть источник» — `eye-off` icon, warn color.
   - Cancel-row внизу в `surface2`.
4. `AddToSpaceSheet`:
   - Title block (mono + 20/w700).
   - Dashed-border tile «Создать новое пространство» с lime `+`.
   - Список user collections, каждая row: tone-swatch 36×36 + title.
5. `Toast`:
   - pinned bottom 90px от родительского контейнера (живёт внутри shell, не root Scaffold).
   - `ink` bg, белый текст 13/500.
   - optional `Вернуть` кнопка в `lime`.
   - slide+fade-in 220ms; auto-dismiss — caller передаёт длительность (2600 / 3400ms).

## Acceptance criteria

- [ ] Widget-test: открытие `CardMenu` блокирует скролл на экране под ним.
- [ ] Golden на каждое открытое состояние.
- [ ] Тап Cancel / scrim фирит `onClose` **ровно 1 раз**.

## Do NOT

- Не использовать `showModalBottomSheet` — overlays живут внутри `ResponsiveShell`, не в root `Scaffold`.
- Не трогать logic-уровень `interactions` / repositories.
