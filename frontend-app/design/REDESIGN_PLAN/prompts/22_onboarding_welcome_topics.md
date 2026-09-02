# Prompt 22 — Onboarding: Welcome + Topics + NFInput

**Phase:** 4 · Screens · **Depends on:** 01, 03, 04
**Agent entry:** `/dev onboarding welcome topics`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 22

## Reference files (read-only)

- `design/reference/mockup/onboarding.jsx` — `WelcomeScreen`, `OnboardingTopics`, `Input`
- `design/reference/mockup/data.jsx` — `INTERESTS`

## Target files

- `lib/screens/onboarding/welcome_screen.dart`
- `lib/screens/onboarding/topics_screen.dart`
- `lib/ui/atoms/nf_input.dart`
- Existing `lib/features/auth/presentation/screens/{login,registration}_screen.dart` → view переходит на `WelcomeScreen` (auth + guest), logic остаётся.
- Existing `lib/features/onboarding/presentation/screens/topic_selection_screen.dart` → view → `topics_screen.dart`.

## Task

### `NFInput`

- Hairline border, radius `NFRadii.radiusSm` (10) для обычного, `NFRadii.radius` (18) для pill-input.
- Padding `EdgeInsets.symmetric(horizontal: 16, vertical: 18)`.
- Label — mono style.
- States: default / focused / error.

### `WelcomeScreen`

- Brand block (logo + wordmark 46/w800).
- Email input + password input (переиспользуй `NFInput`).
- Primary pill «Продолжить».
- Secondary «Войти как гость».

### `TopicsScreen`

- `MIN_TOPICS = 3`, `MAX_TOPICS = 5`.
- Grid chips из `INTERESTS` (23 значения из `data.jsx`).
- CTA становится enabled только когда `selected ∈ [3..5]`.

### Responsive

На desktop / tablet онбординг рендерится **внутри tablet-панели** (520-wide центрированный) — focus mode, не full-bleed.

## Acceptance criteria (Phase-4 template)

- [ ] Рендерится корректно на mobile / tablet / desktop.
- [ ] Matches `WelcomeScreen` / `OnboardingTopics`.
- [ ] Validation работает (min 3, max 5).
- [ ] Existing auth-flow (JWT, email verification) не ломается.
- [ ] Existing integration-тесты проходят.

## Do NOT

- Не трогать `ApiAuthRepository` / `ApiOnboardingRepository`.
- Не менять auth-interceptors.
- Не вводить новые пакеты.
