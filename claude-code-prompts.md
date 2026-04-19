# Радар — Claude Code Implementation Prompts

> Набор промптов для пошаговой имплементации редизайна приложения «Радар».
> Каждый промпт — самодостаточный; запускай по одному в отдельной сессии Claude Code.
> Единый источник истины по токенам: `docs/design-system.md` (создан на предыдущем этапе).
>
> **Рекомендуемый порядок запуска:** строго последовательно, №1 → №13. Пропускать нельзя — каждый следующий промпт предполагает результат предыдущего.
>
> **Перед запуском промпта №1** убедись, что в корне репозитория лежит `docs/design-system.md`. Все промпты ссылаются на его секции (§1, §6.2 и т. д.).

---

## PROMPT 1 — Establish AppTheme with full design token set

**Context for Claude Code:**
This is a Flutter web app targeting mobile (iPhone 14 viewport, 390px width primary). The codebase already has partial `ThemeData` — some tokens exist, some values are hardcoded across widgets. You are setting up the foundation that every subsequent screen will consume.

**Source of truth:**
Read `docs/design-system.md` end-to-end before writing any code. In particular, sections §1 (Colors), §2 (Typography), §3 (Spacing), §4 (Radii), §5 (Gradients), §6 (Elevation), §7 (Motion), §9 (Component tokens), §10 (Flutter implementation guide). Do not invent values — every constant must come from the document.

**Target state:**
A clean `lib/theme/` directory with primitive and semantic tokens organized into separate files, aggregated via a single `AppTokens` class exposed as a `ThemeExtension`. A `buildLightTheme()` function that returns a `ThemeData` combining standard Material 3 `ColorScheme`/`TextTheme` with the `AppTokens` extension attached. Fonts (Inter + Manrope) registered in `pubspec.yaml` with variable weights. No hardcoded colors, sizes, or radii remaining anywhere in the theme layer.

**Specific changes:**
1. Create `lib/theme/tokens/app_colors.dart` — two classes: `_Primitive` (private, holds raw hex from §1.1) and `AppColors` (public, semantic tokens from §1.3). Include the `Accent` record type for pastel pairs (fg + bg) from §1.2 with variants: `sky`, `violet`, `rose`, `amber`, `mint`, `coral`.
2. Create `lib/theme/tokens/app_typography.dart` — class `AppTypography` with named `TextStyle` getters matching §2.2 exactly: `displayXl`, `displayL`, `headingL`, `headingM`, `headingS`, `bodyL`, `bodyM`, `bodyS`, `caption`, `button`, `overline`, `numeric`. Each style uses `Manrope` for display/headingL and `Inter` for the rest, per §2.1. Include a static method `toTextTheme()` that maps these to Material 3 `TextTheme` roles.
3. Create `lib/theme/tokens/app_spacing.dart` — class `AppSpacing` with `static const double` fields: `xs2 = 4`, `xs = 8`, `sm = 12`, `md = 16`, `lg = 20`, `xl = 24`, `xl2 = 32`, `xl3 = 40`, `xl4 = 56` (matching §3.1).
4. Create `lib/theme/tokens/app_radii.dart` — class `AppRadii` with `static const Radius` and corresponding `BorderRadius.all()` for each level from §4.1: `xs`, `sm`, `md`, `lg`, `xl`, `xl2`, `full`.
5. Create `lib/theme/tokens/app_elevation.dart` — class `AppElevation` with `static const List<BoxShadow>` for each level `elev0` through `elev4`, values exactly as specified in §6.2. Include a separate `glassNav` and `glassModal` from §6.3.
6. Create `lib/theme/tokens/app_gradients.dart` — class `AppGradients` with `LinearGradient`/`RadialGradient` getters for `ctaPrimary`, `loginHero`, `glass` (light and strong variants), plus a static widget builder `auroraBackground(Widget child)` that composes the two radial gradients from §5.1 in a `Stack` wrapped in `RepaintBoundary`.
7. Create `lib/theme/tokens/app_motion.dart` — class `AppMotion` with `static const Duration` fields (`instant`, `micro`, `fast`, `base`, `emphasized`, `ambient` from §7.2) and `static const Curve` fields (`standard`, `expressive`, `snappy`, `spring` from §7.1 — use `Cubic(a, b, c, d)` constructors with exact coefficients).
8. Create `lib/theme/app_tokens.dart` — class `AppTokens extends ThemeExtension<AppTokens>`. Aggregates all the above. Implements `copyWith()` and `lerp()` correctly (for lerp, interpolate colors via `Color.lerp`, return `this` for non-interpolable fields like durations). Provide `static final AppTokens light` with all values populated.
9. Create `lib/theme/app_theme.dart` — function `ThemeData buildLightTheme()` that returns a `ThemeData.light()` with: `useMaterial3: true`, `colorScheme` built via `ColorScheme.light(...)` using the mapping from §10.3, `textTheme: AppTypography.toTextTheme()`, `extensions: [AppTokens.light]`, and `scaffoldBackgroundColor: AppTokens.light.colors.surface.background`.
10. Create `lib/theme/theme_context_extension.dart` — `extension ThemeX on BuildContext` with getters `tokens` (returns `Theme.of(this).extension<AppTokens>()!`), `colors`, `typography`, etc. for ergonomic access like `context.tokens.colors.surface.base`.
11. Update `pubspec.yaml` — register Inter and Manrope as custom fonts. Download variable font files from `https://rsms.me/inter/` and `https://www.fontshare.com/fonts/manrope`, place under `assets/fonts/`. Declare all used weights (400, 500, 600, 700).
12. Wire the new theme in `MaterialApp`: replace existing `theme:` argument with `buildLightTheme()`. Remove any existing hardcoded theme overrides that would conflict.

**Do NOT change:**
- Any business logic, routing, state management (bloc/provider/riverpod), or API layer
- Widget tree structure of existing screens — only the theme layer in this prompt
- Any package dependencies beyond font registration
- `.env`, build configs, CI/CD
- Localization files

**Acceptance criteria:**
- [ ] `flutter analyze` passes with zero warnings in `lib/theme/`
- [ ] All primitive hex values from §1.1 appear exactly once (in `_Primitive`), never duplicated
- [ ] `AppTokens.light` instantiates without error
- [ ] `context.tokens.colors.interactive.primary` returns `Color(0xFF3D5BFF)`
- [ ] `context.tokens.typography.headingL.fontFamily == 'Manrope'`
- [ ] Inter and Manrope render correctly (verified by running app and inspecting any existing screen)
- [ ] No file outside `lib/theme/` imports primitive colors directly — all access via `AppTokens` extension

---

## PROMPT 2 — App Shell and Bottom Navigation

**Context for Claude Code:**
Design tokens from Prompt 1 are in place (`context.tokens.*` is available throughout the app). You are now building the app shell that wraps all authenticated screens (Feed, Collections, Profile, Settings). The bottom navigation currently uses default Material `NavigationBar` with blue underline on the active item. Per redesign, it becomes a glass-blurred bar with pill-shaped active indicators.

**Source of truth:**
Read `docs/design-system.md` §6.3 (Glass depth), §9.4 (BottomNav component tokens), §7.3 (press feedback pattern), §10.6 (performance checklist for BackdropFilter).

**Current state:**
Bottom navigation is a `NavigationBar` or `BottomNavigationBar` with 4 items: Лента, Коллекции, Профиль, Настройки. Active item has a blue underline (`primary-500`). Background is flat white. No blur, no pill.

**Target state:**
Bottom navigation becomes a glassmorphic bar: semi-transparent white gradient, `backdrop-filter: blur(32px) saturate(140%)`, 1px white top border, soft shadow projecting upward. Active item renders as a pill-shaped capsule (`primary-50` fill, `radius-full`) containing icon + label, both in `primary-500`. Inactive items: icon in `text.tertiary`, label in caption style. Transition between active states animates the pill position with `duration-base` + `curves.expressive`. Total bar height: 72px + `MediaQuery.padding.bottom` (safe area).

**Specific changes:**
1. Create `lib/widgets/app_shell.dart` — `AppShell` widget accepting a `child` and a `currentRoute`. Wraps the child in a `Scaffold` with `extendBody: true`, `scaffoldBackgroundColor: Colors.transparent`, and a custom `bottomNavigationBar`.
2. Apply `AppGradients.auroraBackground(...)` from Prompt 1 as the outermost `Stack` layer inside `AppShell` — the aurora sits behind everything per §5.1.
3. Create `lib/widgets/glass_bottom_nav.dart` — new widget replacing any existing `NavigationBar`. Structure: `ClipRect` → `BackdropFilter(filter: ImageFilter.blur(sigmaX: 32, sigmaY: 32))` → `Container` with the glass gradient from §5.4 and `border-top: 1px rgba(255,255,255,0.7)`.
4. Inside the glass container, render a `Row` of 4 `_NavItem` widgets (Лента/home, Коллекции/bookmarks, Профиль/person, Настройки/settings icons).
5. `_NavItem` builds as: an outer `GestureDetector` with tap area of 64×56, containing an `AnimatedContainer` (duration `duration-base`, curve `curves.expressive`) that switches between two states — active (`decoration: BoxDecoration(color: tokens.colors.interactive.primarySubtle, borderRadius: AppRadii.full)`) and inactive (`transparent`). Inside: icon 24px + label in `caption` style.
6. Icon color binds to state: active → `interactive.primary`, inactive → `text.tertiary`. Label color mirrors.
7. Apply press feedback per §7.3: wrap each item in an `AnimatedScale` controlled by `GestureDetector` `onTapDown`/`onTapUp`, scale from `1.0` to `0.94` in `duration-micro`.
8. Add upward shadow on the glass container: `BoxShadow(color: Color.fromRGBO(15, 24, 40, 0.06), offset: Offset(0, -8), blurRadius: 32)`.
9. Height calculation: `72 + MediaQuery.of(context).padding.bottom`, exposed via `SafeArea(top: false, bottom: true, child: ...)`.
10. In the main router (go_router/auto_route config), wrap the 4 authenticated routes (feed, collections, profile, settings) with `AppShell`. Unauthenticated routes (login, register, OTP, onboarding) bypass the shell entirely.
11. Add a `RepaintBoundary` around the bottom nav — the glass-blur effect recomputes constantly during scroll, boundary prevents invalidation cascading to the shell's child.

**Do NOT change:**
- Route definitions or navigation logic — only swap which widget wraps the routes
- Business logic inside individual screen widgets
- The icons themselves (keep current Material Symbols if used), only their color treatment
- Back navigation behavior

**Acceptance criteria:**
- [ ] Bottom nav renders with visible blur on scrollable content beneath it
- [ ] Switching tabs animates the active pill smoothly over `duration-base`
- [ ] Tap feedback produces a subtle scale-down
- [ ] Safe-area respected on devices with home indicator
- [ ] On 390×844 viewport (iPhone 14), total nav height is 72 + safe area, pill height is 48
- [ ] No hardcoded colors — all from `context.tokens`
- [ ] `BackdropFilter` appears exactly once in this widget (no nested blurs)

---

## PROMPT 3 — OTP Code Verification Screen

**Context for Claude Code:**
Design tokens (Prompt 1) and app shell (Prompt 2) are live. This screen sits outside the shell (unauthenticated flow). Current implementation uses four underlined input fields, simple layout, disabled-state purple button. Redesign transforms inputs into rounded cells and groups content into a glass-lifted card.

**Source of truth:**
Read `docs/design-system.md` §9.9 (OTPCell component tokens), §9.3 (Input focused/error states for halo logic), §5.1 (aurora background), §6.3 (glass-modal style for the container), §7.3 (press feedback).

**Current state:**
Screen shows a centered card with title «Код подтверждения», subtitle with email, countdown timer `03:52`, four horizontal underlined digit inputs, a muted-purple `Подтвердить` button, and a `Отправить повторно` link. Background is flat `#E5E7EB`.

**Target state:**
Aurora-gradient background behind everything. Content card is a glass-lifted container (`surface-glass` + blur + `radius-xl` + `elev-3`), centered vertically at ~40% of screen height to leave room for keyboard. Title in `heading-l`, subtitle in `body-m` with email highlighted in weight 600. Timer uses `numeric` style with tabular-nums. Four OTP cells replace the underlines: each 56×64, `radius-md`, white surface, 1.5px border, with active-cell focus halo and filled-cell muted border. Primary button uses `gradient.ctaPrimary` (disabled → 0.4 opacity, enabled → full). Resend link in `body-s` + `primary-500` weight 600, activates only when timer hits 00:00.

**Specific changes:**
1. Wrap the screen `Scaffold` body in `AppGradients.auroraBackground(...)` (from Prompt 1). `Scaffold.backgroundColor = Colors.transparent`.
2. Center a content container vertically at 40% screen height (use `Align(alignment: Alignment(0, -0.2), child: ...)`).
3. Container itself is the glass card: `ClipRRect(borderRadius: AppRadii.xl)` → `BackdropFilter(blur 40)` → `Container(decoration: glass-modal decoration from §6.3)` with `padding: EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xl2)`, max width 340.
4. Replace title `Text` style with `context.tokens.typography.headingL`, color `text.primary`. Center aligned.
5. Subtitle: `RichText` with two `TextSpan`s — first in `bodyM` + `text.secondary`, second (the email) in `bodyM.copyWith(fontWeight: w600, color: text.primary)`. Keep "Введите код, отправленный на" + the email value.
6. Timer: replace current style with `context.tokens.typography.numeric` + `fontFeatures: [FontFeature.tabularFigures()]`. Color binds to state — `text.secondary` while running, `text.error` when 00:00 reached.
7. Create `lib/widgets/otp_input.dart` — `OtpInput` widget accepting `length: 4`, `onCompleted: ValueChanged<String>`. Renders a `Row` with `MainAxisAlignment.center` and `Wrap.spacing: AppSpacing.sm` (12px gap).
8. Each cell is a `_OtpCell` — `AnimatedContainer(duration: AppMotion.fast, curve: AppMotion.standard)` with size 56×64, `decoration` switching between three states (empty/active/filled) per §9.9.
9. Active cell decoration: border 1.5px `border.focus` + `boxShadow: [BoxShadow(color: border.focusHalo, spreadRadius: 4, blurRadius: 0)]`.
10. Filled cell decoration: border 1.5px `border.focus.withOpacity(0.4)`.
11. Cell displays the digit using `headingL` style. Use a single hidden `TextField` with `autofocus: true` driving all four cells (standard Flutter OTP pattern) — keyboard type `number`, max length 4, no visible decoration.
12. Primary button: replace current with a `GestureDetector` + `AnimatedContainer` wrapping `Center(child: Text('Подтвердить', style: button))`. Height 56, `radius-lg`, decoration when enabled uses `AppGradients.ctaPrimary` + `elev-2`; when disabled uses the same gradient with `.withOpacity(0.4)` and no shadow. Text always `text.onPrimary`.
13. Button enablement: enabled only when 4 digits entered.
14. Press feedback on button: scale `1.0 → 0.97` in `duration-micro` on `onTapDown`.
15. Resend link: `TextButton` style with `body-s` + weight 600 + color conditional on timer (disabled → `text.disabled`, enabled → `interactive.primary`).
16. Bottom safe-area padding so no clipping on devices with home indicator.

**Do NOT change:**
- Timer countdown logic, API call for verification, routing on success/failure
- The email value resolution (comes from route args or state)
- Haptics if already present — only add if they were absent

**Acceptance criteria:**
- [ ] On 390px width, the card is ~300px wide, centered, with comfortable margins
- [ ] OTP cells gap is exactly 12px; total cells row width ≈ 260px
- [ ] Active cell has visible halo; filled cell shows digit in `heading-l`
- [ ] Timer shows `03:52` with non-shifting digits (tabular figures working)
- [ ] Button disabled visually until 4 digits entered
- [ ] Keyboard appears on screen mount, doesn't cover the card
- [ ] Aurora background visible behind the glass card
- [ ] No hardcoded hex/size values — all from tokens

---

## PROMPT 4 — Login Screen

**Context for Claude Code:**
Tokens and shell in place. Login is the entry point for returning users. Current implementation: plain gradient background (mild), «Радар» pill at top, hero title, two inputs with red error borders already visible at rest, primary button «Войти», secondary button «Регистрация», `Забыли пароль?` link.

**Source of truth:**
Read `docs/design-system.md` §5.3 (login hero gradient), §9.2 (Button tokens), §9.3 (Input tokens — focus/error halo states), §2.2 (display-l, body-l typography).

**Current state:**
Background is a muted gradient. Title «Открывайте контент, который вам понравится ✨» rendered in default weight. Inputs have red borders by default (should only show on error). Error text «Введите эл. почту» / «Введите пароль» is displayed at rest. Button «Войти» is a solid `primary-500` fill. Tap targets feel cramped.

**Target state:**
Background uses `gradient.loginHero` (§5.3) — a gentle 160° linear gradient from blue-tinted white through violet-tinted white to rose-tinted white. «Радар» pill becomes a glass-style capsule. Hero title in `display-l` with negative letter-spacing. Inputs sit at rest in neutral state (no red unless actual validation failure). Focus produces a blue halo per §9.3. Primary button uses `gradient.ctaPrimary` with press scale. Secondary button is ghost-style with border. Link styled consistently.

**Specific changes:**
1. Set `Scaffold.body` background to a `Container` with `decoration: BoxDecoration(gradient: AppGradients.loginHero)`. Remove any existing background color.
2. «Радар» pill: wrap in `ClipRRect(borderRadius: AppRadii.full)` → `BackdropFilter(blur 20)` → `Container` with `decoration: BoxDecoration(color: Colors.white.withOpacity(0.68), border: Border.all(color: Colors.white.withOpacity(0.7), width: 1), boxShadow: AppElevation.elev1)`. Padding `EdgeInsets.symmetric(horizontal: 16, vertical: 8)`. Icon 18px + text `body-s` weight 600 in `interactive.primary`.
3. Hero title: `Text` widget with `context.tokens.typography.displayL`, `text-primary`, `textAlign: TextAlign.center`, max 3 lines. Preserve the ✨ emoji at the end.
4. Subtitle: `bodyL` + `text.secondary`, `textAlign: TextAlign.center`, constrained to `max-width: 320` via `ConstrainedBox`.
5. Email field: use (or create if missing) `lib/widgets/app_text_field.dart` `AppTextField` that wraps Flutter's `TextField` with token-based decoration. Props: `label`, `controller`, `keyboardType`, `obscureText`, `errorText` (optional), `suffixIcon`. 
6. `AppTextField` decoration: `InputDecoration(filled: true, fillColor: context.tokens.colors.surface.base, contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18), border: OutlineInputBorder(borderRadius: AppRadii.md, borderSide: BorderSide(color: border.default, width: 1.5)), focusedBorder: same with border.focus, errorBorder: same with border.error, prefixIcon: leading icon in text.tertiary)`. Height computed to 56.
7. Focus halo: implement via `Focus` widget wrapping the `TextField`, listen to focus state, wrap in `AnimatedContainer` adding `boxShadow` = `[BoxShadow(color: border.focusHalo, spreadRadius: 4, blurRadius: 0)]` when focused, empty list otherwise. Transition `duration-fast`.
8. Error rendering: only show error text + red border when `errorText != null` (i.e., actual validation failed, not at rest). Error text style: `bodyS` + `text.error`, icon ⓘ 14px prefix, gap 4px, margin-top 8px.
9. `Войти` button: `lib/widgets/primary_button.dart` `PrimaryButton` widget (reusable). Container with `AppGradients.ctaPrimary`, height 56, `radius-lg`, text `button` + `text.onPrimary`, `elev-2`, press scale 0.97 via `AnimatedScale`, full-width.
10. `Регистрация` button: `lib/widgets/secondary_button.dart` `SecondaryButton`. White surface + 1.5px `border.default`, height 56, `radius-lg`, text `button` + `text.primary`, elev-0, press produces background tint to `surface.backgroundSubtle`.
11. `Забыли пароль?` link: `TextButton` with style `bodyS` + weight 600 + color `interactive.primary`. Tap target minimum 40px height via padding.
12. Spacing: vertical gap between title and subtitle = `xs` (8), between subtitle and form = `xl2` (32), between form fields = `md` (16), between form and primary button = `xl` (24), between primary and secondary buttons = `sm` (12). Overall screen padding `AppSpacing.lg` (20) on sides.

**Do NOT change:**
- Form validation rules and timing, authentication request handling
- Routing to register/OTP/feed after submit
- Controllers, form keys, existing state management
- Password visibility toggle logic (only restyle if needed)

**Acceptance criteria:**
- [ ] Inputs at rest have neutral grey border, not red
- [ ] Focus on an input produces a visible blue halo
- [ ] Submitting with empty fields produces red border + error text only after submission
- [ ] Primary button gradient visible, scales down on press
- [ ] Secondary button has border, no gradient
- [ ] On 390px width, form width ~350, comfortable margins
- [ ] Background gradient visible, not flat grey
- [ ] All colors/sizes via `context.tokens`

---

## PROMPT 5 — Register Screen

**Context for Claude Code:**
Tokens, shell, and login styling are in place. Register is the sibling of login with three fields instead of two and a different hero copy. Most components (`AppTextField`, `PrimaryButton`, `SecondaryButton`, `Радар` pill) already exist from Prompt 4 and should be reused verbatim.

**Source of truth:**
Read `docs/design-system.md` §5.3 (login hero gradient — same background as login), §9.3 (Input — including success state for password match indicator).

**Current state:**
Same visual base as login (before Prompt 4 applied), with three fields: Эл. почта, Пароль, Подтвердите пароль. Title «Присоединяйтесь ✨» with subtitle. Button «Зарегистрироваться». Link back to login.

**Target state:**
Visually twinned with the redesigned Login (Prompt 4). Reuse all widgets. Add a password-match indicator on the «Подтвердите пароль» field: a success checkmark icon appears in the field's suffix when both password fields have content and match.

**Specific changes:**
1. Apply the same background (`AppGradients.loginHero`) as Prompt 4.
2. Use the existing `Радар` pill component unchanged.
3. Title: `context.tokens.typography.displayL`, text «Присоединяйтесь ✨», center aligned, preserve emoji.
4. Subtitle: `bodyL` + `text.secondary`, «Создайте аккаунт и начните получать персональные рекомендации.».
5. Reuse `AppTextField` for all three inputs. The third field (`Подтвердите пароль`) receives a new prop `trailingStatus` of type `_FieldStatus` enum (`none`, `success`, `error`).
6. In `AppTextField`, when `trailingStatus == success`, render a green check icon (Material Symbols `check_circle` or `check`) in the suffix, 18px, color `context.tokens.colors.text.success` (= `success-500`), with a `duration-fast` fade-in via `AnimatedSwitcher`.
7. On the register screen, compute `trailingStatus` reactively: `success` when both password fields are non-empty AND equal, otherwise `none` (do not show error until form submission).
8. `Зарегистрироваться` button: reuse `PrimaryButton`.
9. `Войти` button: reuse `SecondaryButton`.
10. Spacing identical to Login: `md` between fields, `xl` before primary CTA, `sm` between primary and secondary.
11. Form width and screen padding: identical to Login.

**Do NOT change:**
- Registration submit flow, validation logic, routing to OTP on success
- Password strength rules (only the visual success indicator is added)
- Form state management

**Acceptance criteria:**
- [ ] Visually twin with Login — same gradient, pill, typography scale
- [ ] Three fields render with identical styling to Login inputs
- [ ] Typing matching passwords in fields 2 and 3 shows a green check in field 3's suffix
- [ ] Clearing either password or making them differ removes the check
- [ ] All shared components (`AppTextField`, `PrimaryButton`, `SecondaryButton`) are reused, not duplicated
- [ ] All colors/sizes via tokens

---

## PROMPT 6 — Interest Selection Onboarding Screen

**Context for Claude Code:**
Tokens and auth screens in place. This is the onboarding step shown after registration / first login: user picks 3–5 interests from a chip cloud to seed recommendations. Current implementation has a progress bar, title, subtitle, chip grid, and disabled «Продолжить» button at the bottom.

**Source of truth:**
Read `docs/design-system.md` §9.6 (Chip component tokens — inactive/active states), §9.2 (PrimaryButton), §2.2 (display-l, body-m, caption).

**Current state:**
Top has back arrow + progress bar (2/3 filled). Title «Что вас интересует?», subtitle «Выберите от 3 до 5 тем.». Below — chips for each theme (Политика, Экономика, Технологии, etc.) each with a leading icon, in a flowing wrap layout. Chips are plain grey pills. Bottom has «Продолжить» button, disabled-looking.

**Target state:**
Progress bar becomes gradient-filled. Title in `display-l`. Subtitle retained but augmented with a live counter on the right: «Выбрано: 2/5» in `body-s` + `text.tertiary`, updating reactively. Chips adopt the two-state token styling: inactive = white surface + soft border + dark text + grey icon, active = `gradient.ctaPrimary` fill + white text and icon + `elev-1`. Chip tap animates with scale + color transition. Button at the bottom is sticky via `Scaffold.bottomNavigationBar` or `Align(bottom)` with safe-area padding; disabled state uses `ctaPrimary.withOpacity(0.4)` with an ambient pulse hint that briefly fades between 1.0 and 0.7 opacity in `duration-ambient` the moment it becomes enabled (3rd selection made).

**Specific changes:**
1. Background: `AppGradients.auroraBackground(...)`. Scaffold transparent.
2. Top row: back arrow icon-button (44×44 tap area, icon color `text.primary`) + progress bar. Progress bar is a `Container` of height 6, `radius-full`, `color: AppTokens.light.colors.surface.sunken` for track, with a `FractionallySizedBox` child 0→1 filled via `AppGradients.ctaPrimary`. Animate to new progress value with `duration-emphasized` + `curves.expressive`.
3. Title: `Text('Что вас интересует?', style: context.tokens.typography.displayL)`.
4. Subtitle row: `Row` with `MainAxisAlignment.spaceBetween`. Left: `Text('Выберите от 3 до 5 тем.', style: bodyM + text.secondary)`. Right: `Text('Выбрано: $n/5', style: bodyS + text.tertiary)`, updates reactively from selection state.
5. Create `lib/widgets/interest_chip.dart` — `InterestChip` widget. Props: `label`, `icon`, `selected: bool`, `onTap: VoidCallback`. Build: `GestureDetector` → `AnimatedContainer(duration: AppMotion.fast, curve: AppMotion.snappy)` → `Row(iconSmall, gap 4px, text)`.
6. Inactive decoration: `color: surface.base, border: Border.all(color: border.default, width: 1.5), borderRadius: AppRadii.full`, padding `10 × 16`, icon color `text.secondary`, text `button` + `text.primary`.
7. Active decoration: `gradient: AppGradients.ctaPrimary, boxShadow: AppElevation.elev1`, no border, icon color white, text `button` + `text.onPrimary`.
8. Tap feedback: wrap chip content in `AnimatedScale(scale: _pressed ? 0.94 : 1.0, duration: AppMotion.micro)`.
9. Chip list renders in a `Wrap(spacing: AppSpacing.sm, runSpacing: AppSpacing.sm)`.
10. «Продолжить» button: position via `Scaffold.bottomNavigationBar` with `SafeArea(top: false)` wrapper + 20px horizontal padding + 16px vertical padding. Use `PrimaryButton` from Prompt 4. Disabled state: `onPressed: null` + visual opacity 0.4.
11. Enable/disable logic: enabled when selection count is 3–5 inclusive.
12. Ambient pulse on enable: when button transitions from disabled → enabled, trigger a one-shot `AnimationController` (duration `ambient`, 800ms) animating `boxShadow` intensity (interpolating alpha in the primary-tinted shadow from `elev-2` to 1.4× and back). Use `AnimatedBuilder` around the button.
13. Overall padding: `ListView` with `padding: EdgeInsets.fromLTRB(20, 16, 20, 24)`, but bottom button excluded (it's `bottomNavigationBar`).

**Do NOT change:**
- Interest list data (themes come from existing source)
- Submit logic that sends selections to backend
- Progress value calculation
- Route navigation on «Продолжить»

**Acceptance criteria:**
- [ ] Progress bar visibly filled with gradient, animates on mount
- [ ] Tapping a chip toggles it between white+border and gradient+white
- [ ] Selection counter in the subtitle updates instantly on tap
- [ ] Button disabled when count < 3 or > 5
- [ ] Button transitions visually from muted to full vibrancy at count = 3
- [ ] Subtle pulse animation occurs once when button first becomes enabled
- [ ] Chip tap scales down briefly (0.94) before returning
- [ ] Bottom button respects safe area
- [ ] All tokens, no hardcoded values

---

## PROMPT 7 — Collections Screen (empty state)

**Context for Claude Code:**
Tokens and app shell in place. The Collections screen renders inside `AppShell` (Prompt 2). Current state shows three tabs (Закладки, Нравится, Не нравится) with plain underline-active-indicator styling, and an empty state «Нет сохранённых статей» as flat grey text.

**Source of truth:**
Read `docs/design-system.md` §9.5 (SegmentedControl tokens), §2.2 (typography), §10.4 (where empty states should feel emotional).

**Current state:**
Header «Коллекции» with a leading icon (small, monochrome). Three tabs in a row with a blue underline under the active tab. Empty-state area: flat grey text centered «Нет сохранённых статей».

**Target state:**
Header «Коллекции» in `heading-l`, with a 32×32 icon tile (`accent-violet` pair from §1.2) to its left — matches the visual language of Profile/Settings icon tiles. Tabs convert to a segmented control per §9.5: rounded-pill track in `surface.sunken`, active tab as a white pill with `elev-1`, text transitions in tokens. Empty state: illustration + heading-s + body-m description, centered in the remaining vertical space.

**Specific changes:**
1. Top-level `Column`: header → tabs → body.
2. Header row: `Row(children: [_iconTile, sm gap, Text('Коллекции', style: headingL)])`. `_iconTile` is a new reusable `lib/widgets/icon_tile.dart` `IconTile(size: 32, variant: AccentVariant.violet, icon: Icons.collections_bookmark_rounded, iconSize: 18)`. Decoration uses `AccentVariant.violet.bg` fill and `radius-md`.
3. Tabs: replace with `lib/widgets/segmented_control.dart` `SegmentedControl` widget. Props: `labels: List<String>`, `icons: List<IconData>` (optional, shown before each label), `selectedIndex: int`, `onChanged: ValueChanged<int>`.
4. `SegmentedControl` build: outer `Container` height 44, `decoration: BoxDecoration(color: context.tokens.colors.surface.sunken, borderRadius: AppRadii.full)`, padding `EdgeInsets.all(4)`. Inside: `Stack` — a positioned animated `_thumb` rectangle (white, `radius-full`, `elev-1`) whose `left` offset animates to the selected segment position over `duration-base` with `curves.expressive`, plus a `Row` of tappable segments each occupying `1 / labels.length` width.
5. Active segment text: `button` + `interactive.primary`. Inactive: `button` + `text.secondary`. Icon colors follow text.
6. Thumb position computed via `LayoutBuilder` — `left = constraints.maxWidth / labels.length * selectedIndex` with a 4px inset from track.
7. Margin around the segmented control: `EdgeInsets.fromLTRB(20, 16, 20, 24)`.
8. Empty state: `Expanded` → `Center` → `Column(mainAxisSize: min, children: [illustration, 24 gap, headingText, 8 gap, subtext])`.
9. Illustration: 160×160 area. If no asset exists, use a placeholder `Container` with `decoration: BoxDecoration(gradient: RadialGradient(colors: [accent-violet.bg, transparent]))` containing a bookmark icon at 64px in `accent-violet.fg`. (Later can be swapped for an SVG.)
10. Heading text: `heading-s` + `text.primary`, «Пока ничего не сохранено».
11. Subtext: `body-m` + `text.tertiary`, center aligned, «Сохраняйте статьи, чтобы вернуться к ним позже», max width 280 via `ConstrainedBox`.
12. Screen padding `AppSpacing.lg` horizontal.

**Do NOT change:**
- Tab state management (which tab is active, data loading per tab)
- Bookmarks/likes/dislikes data source
- Item rendering when data exists (that's a separate future prompt)

**Acceptance criteria:**
- [ ] Header shows the violet icon tile + «Коллекции» title
- [ ] Three tabs render in a rounded-pill track; active tab is a white pill that slides on change
- [ ] Tapping a tab animates the thumb to the new position over ~320ms
- [ ] Empty state is visually centered with illustration + two lines of text
- [ ] All colors from tokens
- [ ] Works on 390px width without overflow

---

## PROMPT 8 — Profile Screen

**Context for Claude Code:**
Tokens, app shell, and `IconTile` (from Prompt 7) are in place. Profile is a settings-like list rendered inside `AppShell`. Current: round placeholder avatar, email heading, email duplicate subheading, «Бесплатно» badge, a section titled «Контент и настройки», and three list items (Закладки, Интересы, Смена пароля) each with a pastel icon tile.

**Source of truth:**
Read `docs/design-system.md` §9.7 (IconTile), §2.2 (typography scale), §6.2 (elevation), §4 (radii).

**Current state:**
Avatar is a simple grey circle with a person silhouette. Email «kokolo@gmail.com» large, then duplicated smaller. Badge is a grey-bordered pill. Section label «Контент и настройки» in plain weight. List items are separate rounded rectangles with pastel-tile icons.

**Target state:**
Avatar lifts with a white 2px border + `elev-2` shadow. Email uses `heading-l`, duplicate row removed (redundant). Badge «Бесплатно» becomes an amber-tinted pill in caption style. Section label uppercases in `overline` style + `text.tertiary`. Three list items collapse into a single grouped card (`surface.base`, `radius-lg`, `elev-1`) with inset dividers between rows; each row uses `IconTile` with variants `sky` / `violet` / `amber`.

**Specific changes:**
1. Apply aurora background via `AppShell` (already done in Prompt 2).
2. Avatar: `Container(width: 96, height: 96, decoration: BoxDecoration(shape: circle, color: surface.base, border: Border.all(color: Colors.white.withOpacity(0.9), width: 2), boxShadow: AppElevation.elev2))` wrapping the existing avatar image or placeholder icon.
3. Remove the duplicate email row below the main one.
4. Email title: `context.tokens.typography.headingL`, `text-primary`, center aligned, margin-top `md` after avatar.
5. «Бесплатно» badge: create `lib/widgets/status_pill.dart` `StatusPill` with props `label`, `variant` (AccentVariant). For free tier: `variant: amber`. Renders as `Container(height: 28, padding: EdgeInsets.symmetric(horizontal: 12), decoration: BoxDecoration(color: amber.bg, borderRadius: AppRadii.full), child: Row(icon 14px + gap 4 + Text(label, style: caption with dark amber text)))`. Amber text color = `Color(0xFF9B6B00)`.
6. Section label «Контент и настройки»: convert to `overline` style, uppercase the string, letter-spacing preserved, `text.tertiary`. Margin above `xl2` (32), below `sm` (12).
7. List items: wrap all three in a single `Container(decoration: BoxDecoration(color: surface.base, borderRadius: AppRadii.lg, boxShadow: AppElevation.elev1))`. Inside: `Column` of `_ProfileListItem` widgets separated by inset `Divider`.
8. Create `lib/widgets/profile_list_item.dart` `ProfileListItem`. Props: `iconVariant: AccentVariant`, `icon: IconData`, `title: String`, `subtitle: String`, `onTap: VoidCallback`. Layout: `InkWell(onTap)` → `Padding(16 all)` → `Row(IconTile(40) + md gap + Expanded(Column(title headingS, 2 gap, subtitle bodyS text-tertiary)) + chevron right Icons.chevron_right 18px text-tertiary)`. Total height 72.
9. Inset divider between items: `Padding(EdgeInsets.only(left: 72))` → `Divider(height: 1, color: border.soft)`. 72 = 16 (padding) + 40 (tile) + 16 (gap) so divider starts after the tile.
10. Map the three items: Закладки → `iconVariant: sky, icon: Icons.bookmark_outline, title: 'Закладки', subtitle: 'Сохранённые статьи'`. Интересы → `iconVariant: violet, icon: Icons.auto_awesome, title: 'Интересы', subtitle: 'Настройте темы рекомендаций'`. Смена пароля → `iconVariant: amber, icon: Icons.lock_outline, title: 'Смена пароля', subtitle: 'Обновите пароль аккаунта'`.
11. Overall screen padding `AppSpacing.lg` horizontal. Vertical padding top = safe area + 16, bottom = `72 + safe area + lg` (to clear the bottom nav).
12. Replace `ListView` / `Column` wrapper with a `SingleChildScrollView` so the screen scrolls if content overflows.

**Do NOT change:**
- Auth state source (email from user session)
- Tap handlers — preserve navigation to existing routes (bookmarks, interests, password change)
- Subscription status detection logic — `StatusPill` just renders what's passed in

**Acceptance criteria:**
- [ ] Avatar has a soft shadow and white ring, visible against aurora background
- [ ] Email shown once, in `heading-l`
- [ ] Badge is amber-tinted, small, pill-shaped
- [ ] Section label is uppercase, spaced out
- [ ] Three list items share a single card with hairline dividers between them
- [ ] Each item shows a coloured tile matching its variant
- [ ] Tapping a row navigates to the correct screen as before
- [ ] All tokens, no hardcoded values

---

## PROMPT 9 — Settings Screen

**Context for Claude Code:**
Tokens, shell, `IconTile`, `ProfileListItem` (reusable as generic list item) in place. Settings has two sections — АККАУНТ and НАСТРОЙКИ КОНТЕНТА — plus a standalone «Выйти» button. Current layout uses separate rounded cards for each item.

**Source of truth:**
Read `docs/design-system.md` §9.7 (IconTile), §2.2 (overline, caption for section labels), §6.2 (elev-1), §1.3 (semantic colors for destructive action).

**Current state:**
Title «Настройки». Section АККАУНТ with three items: Эл. почта (email on right, no chevron), Пароль и безопасность, Управление подпиской. Section НАСТРОЙКИ КОНТЕНТА with four items: Каталог источников, Скрытые источники, Мои пространства, Мои добавленные. Standalone «Выйти» card with red text. Below: `Версия приложения: v1.0.0+1` in small grey.

**Target state:**
Two grouped cards (one per section), matching Profile's single-card pattern. Section labels in `overline`. Each item uses `IconTile` with appropriate accent variants. «Эл. почта» first row shows email on the right in `body-s` + `text.tertiary`, no chevron (non-interactive info). «Выйти» sits as its own single-item card styled with error accents.

**Specific changes:**
1. Reuse `ProfileListItem` from Prompt 8 as the generic list row. If the widget is too profile-specific in naming, rename it to `AppListItem` and keep the signature.
2. Extend `AppListItem` to accept `trailing: Widget?` (defaults to chevron). If `trailing` is provided, replace the chevron. For «Эл. почта», pass `trailing: Text('kokolo@gmail.com', style: bodyS + text.tertiary)` and `onTap: null` (non-interactive).
3. Extend `AppListItem` to accept an optional `destructive: bool = false`. When true: title color uses `text.error`, icon tile variant ignored in favor of error-tinted fill (`error-50`) with `text.error` icon, chevron hidden.
4. Screen title: `Text('Настройки', style: headingL)`, top padding `safe area + 16`, horizontal `AppSpacing.lg`.
5. Section label widget: create `lib/widgets/section_label.dart` `SectionLabel(text)`. Renders uppercase text in `overline` style + `text.tertiary`. Margin: `top: xl2 (32), bottom: sm (12)`.
6. АККАУНТ section card: `Container` with `decoration: BoxDecoration(color: surface.base, borderRadius: AppRadii.lg, boxShadow: AppElevation.elev1)`, containing `Column` of three `AppListItem`s:
   - Эл. почта — `iconVariant: sky, icon: Icons.email_outlined, title: 'Эл. почта', subtitle: null, trailing: Text(email, bodyS text.tertiary), onTap: null`.
   - Пароль и безопасность — `iconVariant: violet, icon: Icons.lock_outline, title: 'Пароль и безопасность'`.
   - Управление подпиской — `iconVariant: amber, icon: Icons.workspace_premium_outlined, title: 'Управление подпиской'`.
7. НАСТРОЙКИ КОНТЕНТА section card: same structure, four items:
   - Каталог источников — `iconVariant: amber, icon: Icons.folder_outlined`.
   - Скрытые источники — `iconVariant: rose, icon: Icons.visibility_off_outlined`.
   - Мои пространства — `iconVariant: sky, icon: Icons.grid_view_rounded`.
   - Мои добавленные — `iconVariant: mint, icon: Icons.add_box_outlined`.
8. «Выйти» card: single-item card with `boxShadow: AppElevation.elev1`, containing one `AppListItem(destructive: true, icon: Icons.logout, title: 'Выйти', onTap: authBloc.signOut)`. Margin-top `xl` (24).
9. `AppListItem` when `subtitle` is null: center the title vertically in the row; when non-null: two-line layout as before.
10. Inset divider logic from Prompt 8 stays identical.
11. Version footer: `Text('Версия приложения: ${packageInfo.version}+${buildNumber}', style: caption + text.disabled)`, center-aligned, margin-top `xl` (24), bottom padding to clear bottom nav.

**Do NOT change:**
- Sign-out logic, subscription management navigation, source catalog loading
- Version string source (PackageInfo or wherever it currently comes from)
- The navigation from each item to its destination

**Acceptance criteria:**
- [ ] Title «Настройки» in heading-l at top
- [ ] Two section cards clearly separated by their uppercase labels
- [ ] Эл. почта shows email text on the right, no chevron, no tap response
- [ ] «Выйти» card has red title + red-tinted icon tile, no chevron
- [ ] All dividers are inset after the icon tile
- [ ] Version footer small and subtle at the bottom
- [ ] Grouped cards have elev-1 shadow, visible on aurora background
- [ ] All tokens, no hardcoded values

---

## PROMPT 10 — My Spaces List Screen

**Context for Claude Code:**
Tokens, shell, list item widgets in place. «Мои пространства» is reached via Settings. Current: simple back arrow + title, one list row showing a coloured-outline circle, space name «FFF» with «2 источн.» subtitle, and a blue square FAB bottom-right.

**Source of truth:**
Read `docs/design-system.md` §9.8 (FAB tokens), §1.2 (accent pairs for space colors), §9.7 (IconTile pattern — adapted for coloured swatch).

**Current state:**
Top row: back arrow, title «Мои пространства». One row: outline-only circle (orange, ring only, no fill), space name «FFF» heading-s, «2 источн.» subtitle. FAB at bottom-right: solid blue rounded square with plus icon.

**Target state:**
Header cleaner with 44px tap area on back arrow. Space row's colour indicator becomes a filled circle with a neon-like halo (coloured box-shadow at 12% alpha) — gives the space a sense of identity. Space row otherwise follows `AppListItem` pattern. FAB becomes a circular 56px `ctaPrimary`-gradient button with `elev-3`. Add empty state (when no spaces exist): illustration + CTA.

**Specific changes:**
1. Header: `Row([backButton 44×44, xs gap, Text('Мои пространства', style: headingL)])`. Back button: `IconButton` with invisible tap area 44, icon `Icons.arrow_back_ios_new` or `arrow_back`, size 24, color `text.primary`. On press: `Navigator.pop`.
2. Body: `ListView.separated` (or list of `AppListItem`s wrapped in a single card).
3. Create `lib/widgets/space_row.dart` `SpaceRow`. Props: `color: AccentVariant`, `name: String`, `sourceCount: int`, `onTap`. Builds an `AppListItem` with a custom leading widget instead of `IconTile`:
   - Leading: `Container(width: 32, height: 32, decoration: BoxDecoration(shape: circle, color: color.fg, boxShadow: [BoxShadow(color: color.fg.withOpacity(0.24), blurRadius: 0, spreadRadius: 4)]))` — the halo is a zero-blur, 4px-spread shadow for a clean ring.
   - Title: space name in `headingS`.
   - Subtitle: `'$sourceCount источн.'` in `bodyS` + `text.tertiary`.
   - Chevron as default.
4. When the space's colour is stored as a raw hex or colour name in data, map it to the nearest `AccentVariant`. If the current codebase stores raw `Color` values for spaces, add a helper `AccentVariant fromColor(Color c)` that maps to closest accent pair (by hue distance).
5. Wrap the spaces list in a single `Container` card (same pattern as Profile) if the list is short (≤ 5 items). For longer lists: individual cards per item with `sm` gap.
6. FAB: replace existing widget with a new `lib/widgets/app_fab.dart` `AppFab(icon: Icons.add, onPressed)`. Build: `Material(color: transparent)` → `InkWell(borderRadius: AppRadii.full, onTap: onPressed)` → `AnimatedScale(scale: _pressed ? 0.94 : 1.0)` → `Container(width: 56, height: 56, decoration: BoxDecoration(gradient: AppGradients.ctaPrimary, shape: circle, boxShadow: AppElevation.elev3))` → `Icon(icon, color: white, size: 24)`.
7. Position FAB: `Scaffold.floatingActionButton: AppFab(...)` with `floatingActionButtonLocation: FloatingActionButtonLocation.endFloat`. Because this screen is NOT inside `AppShell` (no bottom nav visible here — it's a detail screen from Settings), bottom margin is just safe area + `space-lg`.
8. Empty state (no spaces): replace the list area with `Center` → `Column` containing illustration (`accent-sky`-tinted circle with folder icon), heading-s «Создайте своё пространство», body-m «Группируйте источники по темам и настраивайте ленту под себя», a secondary button «Создать» triggering the same action as FAB.
9. Screen padding: `AppSpacing.lg` horizontal, top = safe area + 16, bottom = 80 + safe area (FAB clearance).

**Do NOT change:**
- Space data loading, CRUD operations, colour storage format
- Navigation to space detail on tap
- FAB tap handler (presumably opens a create-space dialog/sheet)

**Acceptance criteria:**
- [ ] Header back button has 44px tap target
- [ ] Space row shows a filled coloured dot with soft halo, not just an outline
- [ ] FAB is circular, gradient, with the correct shadow
- [ ] Empty state shown when no spaces exist, with actionable CTA
- [ ] Tapping a space navigates to the space detail screen as before
- [ ] All tokens, no hardcoded values

---

## PROMPT 11 — Edit Space Screen

**Context for Claude Code:**
Tokens, shell, inputs, chips in place. «Изменить пространство» is reached by tapping the pencil icon on a space detail screen. Has: name input, colour palette (8 swatches), source search, filter chips, source list with checkboxes.

**Source of truth:**
Read `docs/design-system.md` §9.3 (inputs with floating label and focus halo), §9.6 (chips for source type filter), §1.2 (accent pairs → swatches), §6.2 (elev-1 and elev-2 for swatch states).

**Current state:**
Top row: back arrow, title «Изменить простра…» (truncated), «Сохранить» link. Name input with label. Colour swatches in a row (plain flat circles; selected has a thick dark ring). «Источники» label. Search bar. Filter chips: Все / Telegram / Habr / VC.RU. Source list with checkboxes.

**Target state:**
Header title no longer truncates — allow two lines if needed. Name input uses token styling with floating label and character counter. Colour swatches: filled circles with inner white ring + outer halo + shadow differentiation between selected and unselected. Source search: pill-shaped input with soft background. Filter chips use the active/inactive pattern. Source list checkboxes: custom-styled rounded squares with a fill state.

**Specific changes:**
1. Header: `Row([backButton, Expanded(Text('Изменить пространство', style: headingM, maxLines: 2)), textButtonSave])`. Save button: `TextButton` with child `Text('Сохранить', style: bodyL.copyWith(fontWeight: w600, color: interactive.primary))`, padding `12 × 16` so tap area ≥ 44×44.
2. Name input: use `AppTextField` (from Prompt 4) with floating label behavior. Extend `AppTextField` if needed: when `floatingLabel: bool = false` is true, render the label inside the input border as a floating label (Flutter native supports this via `InputDecoration.labelText` + `floatingLabelBehavior: FloatingLabelBehavior.auto`). Height becomes 56 regardless.
3. Character counter: `AppTextField` gets a new `maxLength` prop. When set, show below-right counter in `caption` + `text.tertiary` with format `$current/$max` (e.g. `3/50`). Hide the default Material counter (`counterText: ''` in decoration).
4. «Цвет» section label: `SectionLabel('Цвет')` (from Prompt 9, lowercase text, but the widget uppercases). Actually the current design uses sentence-case «Цвет» not uppercase «ЦВЕТ» — override: add `uppercase: bool = true` prop to `SectionLabel`, pass `uppercase: false` here. Style: `bodyS` + `text.secondary` in that case (to differentiate from main section headers).
5. Colour palette: create `lib/widgets/colour_swatch_grid.dart` `ColourSwatchGrid`. Props: `variants: List<AccentVariant>`, `selected: AccentVariant`, `onChanged`. Renders a `Wrap(spacing: sm, runSpacing: sm)` of `_Swatch` buttons.
6. `_Swatch` unselected: `Container(width: 40, height: 40, decoration: BoxDecoration(shape: circle, color: variant.fg, boxShadow: AppElevation.elev1))`.
7. `_Swatch` selected: same base + inner white ring (`Border.all(color: white, width: 3)` inset via `Padding(all: 3)` trick, or render a nested smaller circle) + outer halo via `BoxShadow(color: variant.fg.withOpacity(0.4), blurRadius: 0, spreadRadius: 3)` + `elev-2` shadow. Transition between states animates over `duration-fast`.
8. Swatch tap: `AnimatedScale` feedback 0.94 on press.
9. «Источники» section label: reuse `SectionLabel('Источники', uppercase: false)`.
10. Search input: create `lib/widgets/search_field.dart` `SearchField`. Props: `controller, onChanged, placeholder`. Build: `Container(height: 48, decoration: BoxDecoration(color: surface.backgroundSubtle, borderRadius: AppRadii.full))` → `Row(icon search 18px text.tertiary + sm gap + TextField borderless, bodyM, placeholder text.tertiary)`. Padding `horizontal: 16`.
11. Filter chips row: `SingleChildScrollView(horizontal)` with `Row` of `FilterChip` widgets (create `lib/widgets/filter_chip.dart` modelled on `InterestChip` from Prompt 6 but without leading icon). Active uses `ctaPrimary` gradient, inactive uses surface-base + border. Height 36 instead of 44 (chips here are denser).
12. Source list: `ListView.separated` with `_SourceRow` items. Each row height 56.
13. `_SourceRow` layout: `Row(checkbox, sm gap, badge, sm gap, Expanded(Text(name, bodyL + text.primary)))`. 
14. Checkbox: custom `_AppCheckbox` widget. 24×24, `radius-sm`. Unchecked: `Border.all(color: border.default, width: 1.5)` + `color: transparent`. Checked: `color: interactive.primary, borderRadius: AppRadii.sm, boxShadow: AppElevation.elev1` + white check icon 16px. Animate colour transition in `duration-fast`.
15. Source badge: small pill `radius-xs`, background pastel (map per source type: Habr = sky, VC = rose, Telegram = mint), text `caption` + darker matching colour.
16. Screen padding: `AppSpacing.lg` horizontal. Section gaps: `xl` (24) between major sections.

**Do NOT change:**
- Source filtering/loading logic, name edit persistence, save action payload
- The list of available source types
- Space update API call

**Acceptance criteria:**
- [ ] Title no longer truncates — renders fully (may wrap to 2 lines)
- [ ] Name input shows floating label when focused and character counter
- [ ] Colour swatches clearly distinguish selected via inner ring + halo
- [ ] Search input is pill-shaped with soft grey background
- [ ] Filter chips toggle between ghost and gradient states
- [ ] Checkboxes show a clean blue fill with white check when selected
- [ ] Source type badges render in pastel-tinted pills matching their type
- [ ] All tokens, no hardcoded values

---

## PROMPT 12 — Space Detail Screen (Feed + Settings tabs)

**Context for Claude Code:**
Tokens, segmented control, edit-space widgets in place. Space detail shows a specific space's content feed and settings, with a segmented control switching between «Лента» and «Настройки» tabs.

**Source of truth:**
Read `docs/design-system.md` §9.5 (SegmentedControl), §9.1 (Card tokens for feed items — will be reused more heavily in Prompt 13), §9.7 (IconTile), §1.2 (accent halo for space color dot).

**Current state:**
Header: back arrow, coloured dot (orange), space name «FFF», pencil edit icon. Underline-style tabs «Лента» / «Настройки». Feed tab: two article cards. Settings tab: three settings rows (Название, Цвет, Источники), plus a red «Удалить пространство» button.

**Target state:**
Header styled consistently: 44px back button, filled coloured dot with halo (same as space row from Prompt 10), space name in `heading-m`, 44px pencil button. Tabs use `SegmentedControl` from Prompt 7. Feed tab reuses future feed card styling (keep current card basic here — Prompt 13 overhauls it). Settings tab: three rows in a grouped card, each using `AppListItem` pattern. «Цвет» row shows the current space colour as a swatch on the right. «Удалить пространство» button: outlined destructive style.

**Specific changes:**
1. Header row: `Row(spaceBetween, [Row(back button + sm gap + coloured dot 20×20 with halo + sm gap + Text(spaceName, headingM, maxLines: 1, ellipsis)), pencil iconButton 44×44])`. Dot halo uses the same pattern as Prompt 10's `SpaceRow`: 4px spread shadow at 24% alpha.
2. Tabs: use `SegmentedControl(labels: ['Лента', 'Настройки'], selectedIndex, onChanged)`. Wrapped in horizontal padding `AppSpacing.lg`, top margin `md` (16), bottom margin `xl` (24).
3. Body: `IndexedStack` or `TabBarView` switching between `_FeedTab()` and `_SettingsTab()`.
4. `_FeedTab`: leave the current card implementation in place for now (Prompt 13 redesigns cards globally, including this one). Just ensure: padding `AppSpacing.lg` horizontal, card spacing `sm` vertical. DO NOT re-style the cards here.
5. `_SettingsTab`: build a grouped card matching Profile's pattern with three `AppListItem`s:
   - Название — `iconVariant: sky, icon: Icons.edit_outlined, title: 'Название', subtitle: spaceName, onTap: → edit sheet`.
   - Цвет — `iconVariant: amber, icon: Icons.palette_outlined, title: 'Цвет', trailing: _ColourSwatch(color: currentColour, size: 24)`.
   - Источники — `iconVariant: mint, icon: Icons.folder_outlined, title: 'Источники', subtitle: '$count', onTap: → sources edit`.
6. `_ColourSwatch`: small version of the palette swatch — 24×24 filled circle with `elev-1`, no halo. Just a visual indicator of current colour.
7. «Удалить пространство» button: create `lib/widgets/destructive_button.dart` `DestructiveButton(label, icon, onPressed)`. Build: `Container(height: 52, width: double.infinity, decoration: BoxDecoration(color: surface.base, border: Border.all(color: context.tokens.colors.error.withOpacity(0.2), width: 1), borderRadius: AppRadii.lg))` → `Row(center, [Icon(delete_outline, color: error, size: 20), xs gap, Text(label, style: button + error)])`. On press: scale 0.97 feedback + trigger confirm dialog.
8. Confirm dialog on delete: `showDialog` with a custom `AlertDialog` styled per glass-modal tokens (`surface-glass-strong` background, `radius-2xl`, `elev-4`). Buttons: «Отмена» secondary, «Удалить» destructive. Preserve existing delete API call.
9. Margin above destructive button: `xl` (24). Below: safe area + `lg`.

**Do NOT change:**
- Feed data loading for this space's content
- Card rendering in feed tab — reserved for Prompt 13
- Space update/delete API calls
- Tab state persistence

**Acceptance criteria:**
- [ ] Header has filled coloured halo dot + title + pencil button
- [ ] Tabs are a segmented control with animated thumb
- [ ] Feed tab renders articles (temporarily with current card style)
- [ ] Settings tab renders three grouped list items + destructive button
- [ ] Colour swatch in «Цвет» row reflects the current space colour
- [ ] Delete button has outlined error style, triggers confirm dialog
- [ ] Confirm dialog uses glass-modal styling
- [ ] All tokens, no hardcoded values

---

## PROMPT 13 — Main Feed Screen (most complex)

**Context for Claude Code:**
All foundational widgets exist (tokens, shell, cards, buttons, FAB). Main Feed is the most visually dense screen: header, scrollable content cards with images, tap-action icons per card, pagination dots, and a floating action button. This prompt consolidates the card design used both here and in the space feed tab (Prompt 12).

**Source of truth:**
Read `docs/design-system.md` §9.1 (Card), §9.2 (Button.iconGhost for action icons), §9.8 (FAB), §5.1 (aurora background), §6.3 (glass-strong for sticky header), §7.3 (stagger animation pattern).

**Current state:**
Top area: small «Радар» logo with icon + text, then «Для вас» as a separate title. Scrollable list of cards: each has a swipeable image gallery with pagination dots, a title, a description, author + date, three action icons (thumbs up, thumbs down, bookmark). At bottom-right floats a blue rounded-square FAB with a QR/grid icon. Bottom nav handled by `AppShell`.

**Target state:**
Sticky glass header combining logo + title, blurring content that scrolls under it. Aurora background from shell visible in margins. Cards redesigned: `radius-lg`, `elev-2`, cover image inside with `radius-md` and subtle bottom overlay, pagination dots active/inactive differentiation, action icons as ghost circle buttons with press-to-fill feedback. FAB becomes the circular gradient `AppFab`. Cards stagger-fade in on mount.

**Specific changes:**
1. Remove any direct scaffold-background colour — background is the aurora from `AppShell`.
2. Header: create `lib/widgets/sticky_glass_header.dart` `StickyGlassHeader`. Takes `title` + optional leading widget. Renders as: `ClipRect` → `BackdropFilter(blur 32)` → `Container(decoration: BoxDecoration(gradient: gradient.glassStrong), padding: EdgeInsets.fromLTRB(20, safeArea.top + 8, 20, 12), boxShadow: AppElevation.elev2)` → `Row([logoPill, xl2 gap or between-structure, Expanded(Text(title, headingL)))`.
3. Logo pill: small glass pill «Радар» — same as Login (reuse the widget if possible, or create `lib/widgets/brand_pill.dart`). 28px height here (smaller than Login's version), icon 16, text `caption` weight 600.
4. Implement sticky behavior: wrap the content in a `CustomScrollView` with a `SliverPersistentHeader` that always stays pinned at the top. Alternatively, use `NestedScrollView`. Header height collapsed: 56 + safe area.
5. Body: `SliverList` of `FeedCard` widgets.
6. Create `lib/widgets/feed_card.dart` `FeedCard`. Props: `article: Article` (existing model). Build: `Container(decoration: BoxDecoration(color: surface.base, borderRadius: AppRadii.lg, boxShadow: AppElevation.elev2))` → `Column`.
7. Inside card, top to bottom: cover carousel (fixed 180px height) → 16px padding around content (title, description, meta, action row).
8. Cover carousel: `PageView` with `aspectRatio 2:1`, each page is `ClipRRect(borderRadius: AppRadii.md)` wrapping the image. Apply a bottom gradient overlay via `Stack` + a `DecoratedBox` with `linear-gradient(180deg, transparent 60%, rgba(0,0,0,0.08) 100%)` for subtle depth. Page indicators beneath the carousel.
9. Page indicators: `Row(MainAxisAlignment.center)` of animated dots. Active dot: 8×8, `interactive.primary`, `radius-full`. Inactive: 6×6, `text.disabled.withOpacity(0.4)`, `radius-full`. Transition dot sizes over `duration-fast`. Dots positioned with 4px gap.
10. Title: `headingM` + `text.primary`, `maxLines: 2`, `overflow: ellipsis`. Margin-top 16.
11. Description: `bodyM` + `text.secondary`, `maxLines: 3`, `overflow: ellipsis`. Margin-top 4.
12. Meta row: `Row([Text(author, bodyS + text.tertiary), xs gap, dot separator 4×4 rounded, xs gap, Text(date, bodyS + text.tertiary)])`. Margin-top 12.
13. Action row: `Row(MainAxisAlignment.start, spacing: sm)` of three `ActionIconButton`s. Margin-top 12, bottom 16 (card internal).
14. Create `lib/widgets/action_icon_button.dart` `ActionIconButton(icon, active, onTap, activeColor)`. Size 44×44 (with additional invisible padding for 48 tap target). `Container(width: 44, height: 44, decoration: BoxDecoration(shape: circle, color: active ? activeColor.withOpacity(0.1) : transparent))` → `Icon(icon, size: 20, color: active ? activeColor : text.tertiary)`. `AnimatedContainer` for state transitions (`duration-fast`). Scale 0.9 on press.
15. Map the three action buttons: thumbs-up → `activeColor: success-500`, thumbs-down → `activeColor: error-500`, bookmark → `activeColor: interactive.primary`.
16. Card gap in list: `sm` (12) between cards.
17. Overall list padding: top = 0 (header handles), bottom = 72 + safe area + `lg` (to clear nav + breathing room), horizontal = `lg` (20).
18. FAB: use `AppFab` (from Prompt 10) with a grid/QR icon (`Icons.qr_code` or custom). Wire `onPressed` to existing QR/add handler. Position via `Scaffold.floatingActionButton` with `endFloat` and bottom margin = 72 + safe area + `md` (above the nav).
19. Stagger-fade animation on list entries: wrap each `FeedCard` in an `AnimatedOpacity` + `AnimatedSlide` controlled by a staggered `AnimationController`. Delay = `index * 40ms`, max delayed index = 5 (after that, no delay). Duration `duration-base`, curve `curves.expressive`. Apply only on first build, not on scroll.
20. Pull-to-refresh: wrap the sliver list in a `CustomScrollView` with a `CupertinoSliverRefreshControl` or `RefreshIndicator`. Styled to match: use `interactive.primary` colour.

**Do NOT change:**
- Article data source, pagination logic (loading more items on scroll), refresh handler
- Action button state persistence (like/dislike/bookmark API calls)
- FAB tap behavior (whatever it currently does, most likely scanning a QR or opening an add sheet)
- Carousel image loading/caching

**Acceptance criteria:**
- [ ] Sticky header visible with glass blur; content scrolls under it
- [ ] Header shows «Радар» pill + «Для вас» title in heading-l
- [ ] Cards have `radius-lg`, soft elev-2 shadow, visible against aurora
- [ ] Cover carousel swipeable, pagination dots animate correctly (active 8×8, inactive 6×6)
- [ ] Title, description, meta all styled per tokens, correct line limits
- [ ] Three action buttons each 44×44 circle, fill-on-active with their respective colour
- [ ] FAB is circular gradient, positioned above bottom nav
- [ ] First-load stagger animation plays once, cards fade+translate in
- [ ] Pull-to-refresh works
- [ ] On 390px width, cards fit comfortably with 20px margins
- [ ] All tokens, no hardcoded values, no regressions on existing API calls

---

## Post-implementation checks

After all 13 prompts are applied, run this checklist:

1. **Flutter analyze** — zero warnings across `lib/`.
2. **Visual regression sweep** — open each screen on 390px viewport, verify against `design-system.md` §9 component tokens.
3. **Performance spot-check** — DevTools frame overlay on Main Feed scroll; target ≥55 fps average, no red frames during scroll.
4. **Dark theme readiness** — open any screen, swap `ThemeData.light()` → `ThemeData.dark()` temporarily and verify that the app compiles (colours will look wrong, but no crashes). This validates that `AppTokens` extension is structured correctly.
5. **Token coverage** — grep `lib/` for any remaining hex patterns (`Color(0xFF...)` outside `lib/theme/tokens/app_colors.dart`). Should be zero hits.
6. **Font rendering** — verify Manrope and Inter render cleanly on Windows Chrome at all sizes. If artifacts visible on 22px, plan font swap per §12.2 of the design system.

