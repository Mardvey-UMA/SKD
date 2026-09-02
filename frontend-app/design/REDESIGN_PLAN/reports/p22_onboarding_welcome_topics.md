# P22 — Onboarding: WelcomeScreen + TopicsScreen + NFInput

**Slug:** `redesign-p22-onboarding-welcome-topics`
**Spec:** `design/REDESIGN_PLAN/prompts/22_onboarding_welcome_topics.md`
**Source of truth:** `design/reference/mockup/onboarding.jsx` (`WelcomeScreen`,
`OnboardingTopics`, `Input`) + `design/reference/mockup/data.jsx`
(`INTERESTS`).

## Files created

- `lib/screens/onboarding/welcome_screen.dart` — port of JSX `WelcomeScreen`:
  decorative shapes (lime blob, ink ring, rotated accent square), brand block
  (logo + "Радар" wordmark 20/w700/-0.8), display headline «Находите / лучшее
  / из всего.» (responsive via `NFText.display`), subtitle, email +
  password `NFInput` with `user` / `gear` icons, primary pill «Продолжить»
  (accent fill + arrow-up-right icon, `AuthNotifier.login`), secondary pill
  «Войти как гость» (ink outline 1.5px, routes to `/register`).
- `lib/screens/onboarding/topics_screen.dart` — port of JSX
  `OnboardingTopics`: back pill + 3-segment progress track + mono step counter,
  display title «Что вам / интересно?», subtitle, ink counter badge with
  lime/rose dot, wrapping grid of chips, sticky bottom `Продолжить` CTA with
  «Выберите ещё N» fallback, mono footer «ОТ 3 ДО 5 ТЕМ». `MIN_TOPICS = 3`,
  `MAX_TOPICS = 5` as `TopicsScreen` constants.
- `lib/screens/onboarding/data/interests.dart` — verbatim port of `INTERESTS`
  from `data.jsx` (16 entries) × chip colour table from
  `onboarding.jsx § OnboardingTopics.topics`. Russian labels are preserved
  as-in-source.

## Files edited

- `lib/ui/atoms/nf_input.dart` — expanded from the P18 minimum (title +
  description for `CollectionEditorScreen`) to the full P22 spec: hairline
  border (`NFColors.hairline`), default radius `NFRadii.radiusSm = 10`
  (pill variants pass `radius: NFRadii.radius = 18`), padding
  `EdgeInsets.symmetric(horizontal: 16, vertical: 18)` (14 when leading icon
  present to keep overall ~52-px hit-height), optional leading SVG icon
  (`NFIcon`), mono error message below the field, three states
  (`default / focused / error`) via a focus listener + `errorText`.
- `lib/core/router/app_router.dart`:
  - `/login`, `/auth/login` → `WelcomeScreen` (was `LoginScreen`).
  - `/register`, `/auth/register` → existing `RegistrationScreen` (logic
    preserved, `RegistrationScreen` widget untouched).
  - `/onboarding`, `/onboarding/topics` → `TopicsScreen` (was
    `TopicSelectionScreen`).
  - `goingToAuth` extended to include the `/auth/*` aliases so the
    authenticated-user redirect still catches them.
  - Old `LoginScreen` / `TopicSelectionScreen` imports removed.

## Mapping: old auth screens → new Welcome

| Old screen (`lib/features/auth/presentation/screens/`) | Old route | New behaviour |
|---|---|---|
| `LoginScreen` | `/login` | Replaced by `WelcomeScreen` (same route). Primary pill «Продолжить» calls `AuthNotifier.login` through the existing `LoginFormNotifier` — identical validation / error handling. |
| `RegistrationScreen` | `/register` | Kept as-is for the actual sign-up flow (2 password fields, validation). `WelcomeScreen` secondary pill «Войти как гость» routes here (pragmatic mapping — no separate guest mode exists yet). |
| `ForgotPasswordScreen` | `/forgot-password` | Unchanged. Not reachable from `WelcomeScreen` in this iteration (JSX does not surface a «forgot» CTA; can be added later as a tertiary link). |
| `LoginFormNotifier` + `AuthNotifier` + `AuthTokenInterceptor` + `RefreshTokenInterceptor` + `ApiAuthRepository` | — | Untouched. `WelcomeScreen` reuses `loginFormNotifierProvider` + `authNotifierProvider` verbatim, so JWT issuance / refresh / token persistence / `/verify-code` redirect all keep working. |

The dead files (`LoginScreen`, `TopicSelectionScreen`) are no longer imported
by the router and are not referenced elsewhere in `lib/` or `test/`. They
remain on disk (untouched) because deletion is out of scope for P22 — a
cleanup pass can remove them together with the old `shared/widgets/` helpers
in a later redesign prompt.

## Responsive behaviour

Both screens wrap their content in a `_ResponsiveFrame` / `_TopicsFrame`
helper that reads `context.breakpoint`:

- `Breakpoint.mobile` → full-bleed `SafeArea`.
- `Breakpoint.tablet` / `Breakpoint.desktop` → `Center` + `ConstrainedBox`
  (maxWidth = **520**) + surface panel (`NFRadii.radiusLg = 26`, hairline
  border, `NFColors.bg` fill). This is the spec-mandated *focus mode* —
  onboarding stays in the 520-wide column regardless of viewport.

The `ResponsiveShell` is NOT applied to onboarding (user is not yet
authenticated / onboarded, so the bottom-nav / side-nav shell would be
misleading). Focus mode is handled per-screen.

## Interests data

The JSX `INTERESTS` array has **16** entries — the spec mentions "at least 23"
in the internal description, but the JSX (which the spec designates as the
source of truth) ships 16. I ported all 16 verbatim and extended each with
the chip-colour pair from `onboarding.jsx § OnboardingTopics.topics` (14
entries with explicit colours); the last two (`Архитектура`, `Город`) that
don't appear in the JSX colour table fall back to `surface2` + `ink` so new
rows still render correctly.

Backend-side, `ApiOnboardingRepository.completeOnboarding` expects category
IDs. Per `API_CONTRACTS.md § 2.3`, those IDs are lowercase Russian labels
(e.g. `"технологии"`), so the screen submits `label.toLowerCase()` — no
static mapping table required.

## Acceptance criteria

- [x] Matches `WelcomeScreen` / `OnboardingTopics` from the JSX (brand
      block, decorative shapes, display headline, input + two pills on
      welcome; header row with progress track + counter badge + wrap grid +
      sticky CTA on topics).
- [x] Validation works (CTA is a no-op outside `[3, 5]`; fallback label
      shows how many more to select; chips are `Opacity(0.45)` +
      non-tappable when max is reached).
- [x] Existing auth flow (JWT, email verification, `/verify-code` redirect)
      not broken: `WelcomeScreen` reuses `loginFormNotifierProvider` +
      `authNotifierProvider`; `RegistrationScreen` / `EmailVerificationCode`
      / `ForgotPasswordScreen` widgets untouched.
- [x] `flutter analyze` clean on the changed files — only pre-existing
      warnings remain elsewhere in the project.
- [x] `flutter test test/features/auth test/features/onboarding` matches
      baseline (22 passed, 4 pre-existing failures in
      `email_verification_code_screen_test.dart` — present on master
      before P22).
- [x] Responsive behaviour verified via the `_ResponsiveFrame` helper
      (mobile full-bleed, tablet/desktop 520-wide focus panel).
- [x] Pack `ApiAuthRepository`, `ApiOnboardingRepository`,
      `AuthTokenInterceptor`, `RefreshTokenInterceptor` untouched (checked
      via `git status`).

## Tests

```
flutter analyze      → clean on P22 files (21 pre-existing issues elsewhere)
flutter test         → 22 passed, 4 pre-existing failures (baseline)
```

The pre-existing failures live in
`test/features/auth/presentation/screens/email_verification_code_screen_test.dart`
and are unrelated to this prompt (verified by stashing P22 and rerunning
against master — failure count identical).
