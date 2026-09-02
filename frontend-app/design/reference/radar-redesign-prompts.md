# Радар — Flutter Web Redesign Prompts

> Orchestrator input. Each `## Prompt N` block is **self-contained** — the frontend subagent (Claude Code) sees only one block at a time. Do **not** rely on prior context between prompts.

Renderer target: **Flutter web / CanvasKit**.
Mockup source of truth: `design/reference/mockup/`.

---

## Design tokens (reference table)

Extracted verbatim from `design/reference/mockup/tokens.jsx`.

### Colors (`NF`)

| Token         | Value              | Usage                                     |
|---------------|--------------------|-------------------------------------------|
| `bg`          | `#F4F5F2`          | app background                            |
| `surface`     | `#FFFFFF`          | cards, sheets                             |
| `surface2`    | `#ECEDE8`          | muted surface (cancel rows, hero blocks)  |
| `hairline`    | `rgba(17,17,17,0.09)` | all 1px dividers & card borders         |
| `ink`         | `#0E0F0D`          | primary text, primary buttons             |
| `ink2`        | `#2A2B28`          | body copy on light                        |
| `mute`        | `#6E6F6A`          | secondary text, meta                      |
| `mute2`       | `#9A9B96`          | tertiary (3px dot separators)             |
| `accent`      | `#3B2BFF`          | accent fill (liked state, brand accents)  |
| `accentInk`   | `#FFFFFF`          | foreground on accent                      |
| `lime`        | `#CCFF33`          | secondary accent (bookmark fill, premium) |
| `limeInk`     | `#0E0F0D`          | foreground on lime                        |
| `warn`        | `#FF5A1F`          | destructive, hide-source                  |
| `chipBg`      | `#E8E9E3`          | chip background                           |

Tweakable at runtime: `accent`, `lime` (`secondary` in tweaks panel).

### Radius, typography, motion

| Token          | Value                | Flutter equivalent                            |
|----------------|----------------------|-----------------------------------------------|
| `radius`       | `18`                 | `BorderRadius.circular(18)`                   |
| `radiusLg`     | `26`                 | `BorderRadius.circular(26)`                   |
| `radiusSm`     | `10`                 | `BorderRadius.circular(10)`                   |
| `FONT_SANS`    | `'Nunito', -apple-system, system-ui, sans-serif` | family: `Nunito`         |
| `FONT_MONO`    | same as SANS (mono look = uppercase + letter-spacing 0.8, size 10) | same family, apply tracking manually |

**Weights used:** 400, 500, 600, 700, 800, 900 (all from Nunito).

**Motion:** all transitions in source are `160ms`, `180ms`, `220ms`, `260ms`, or `280ms` with a decelerating curve. Map:
- `160–180ms` → `Duration(milliseconds: 180)` + `Curves.easeOut`
- `220–260ms` → `Duration(milliseconds: 240)` + `Curves.easeOutCubic`
- `280ms sheet` → `Duration(milliseconds: 280)` + `Cubic(0.2, 0.9, 0.25, 1)`
- `140ms nav hover` → `Duration(milliseconds: 140)` + `Curves.easeOut`

### Shadows

| Surface                 | Box-shadow (CSS)                                                      |
|-------------------------|-----------------------------------------------------------------------|
| card                    | `0 1px 0 rgba(0,0,0,0.02), 0 8px 24px -16px rgba(0,0,0,0.12)`         |
| bottom nav              | `0 12px 30px -10px rgba(0,0,0,0.18)`                                  |
| toast                   | `0 20px 40px -10px rgba(0,0,0,0.4)`                                   |
| tablet panel            | `0 24px 60px -20px rgba(0,0,0,0.18)`                                  |

Map to `BoxShadow(offset, blurRadius, spreadRadius, color)` — `-Npx` spread in CSS = negative `spreadRadius` in Flutter.

### Breakpoints

| Name     | Range          | Source: `radar-web.html` `useLayout()`  |
|----------|----------------|------------------------------------------|
| mobile   | `< 768px`      | `DeviceFrame` + bottom nav               |
| tablet   | `768–1199px`   | 520-wide centered panel + bottom nav     |
| desktop  | `≥ 1200px`     | 240px `SideNav` + fluid main column      |

---

## Screens index

All screens render **inside** the shell for their breakpoint. Reference the single JSX definition + the three shell branches.

| Screen             | Definition (JSX function)         | File                              | Used in flow                       |
|--------------------|-----------------------------------|-----------------------------------|------------------------------------|
| Welcome            | `WelcomeScreen`                   | `onboarding.jsx`                  | onboarding step 01                 |
| Onboarding Topics  | `OnboardingTopics`                | `onboarding.jsx`                  | onboarding step 02                 |
| Feed               | `FeedScreen`                      | `screens.jsx`                     | tab: `feed`                        |
| Detail             | `DetailScreen`                    | `screens.jsx`                     | view: `detail`                     |
| Related (all)      | `RelatedAllScreen`                | `screens.jsx`                     | view: `related-all`                |
| Bookmarks / Likes / Dislikes | `BookmarksScreen` (kind prop) | `screens.jsx`              | view: `bookmarks` / `likes` / `disliked` |
| Collections        | `CollectionsScreen`               | `screens.jsx`                     | tab: `collections`                 |
| Collection editor  | `CollectionEditorScreen`          | `screens.jsx`                     | view: `collection-edit`            |
| Profile            | `ProfileScreen`                   | `screens.jsx`                     | tab: `profile`                     |
| Settings           | `SettingsScreen`                  | `screens.jsx`                     | tab: `settings`                    |
| Plan (upgrade)     | `PlanScreen`                      | `screens.jsx`                     | view: `plan`                       |
| Sources            | `SourcesScreen`                   | `screens.jsx`                     | view: `sources`                    |
| Add source         | `AddSourceScreen`                 | `screens.jsx`                     | view: `add-source`                 |
| My sources         | `MySourcesScreen`                 | `screens.jsx`                     | view: `my-sources`                 |

**Shell references** (same file across all screens):
- `radar-web.html` → `ResponsiveShell`, `BottomNav`, `SideNav`, `useLayout()`, `DeviceFrame` import.

Acceptance criteria across Phase 4 prompts always include: *"renders correctly at mobile / tablet / desktop breakpoints; layout matches the shell branch in `radar-web.html`"*.

---

## Phase 1 — Foundation

### Prompt 1. Theme tokens

**Phase:** 1 · Foundation
**Depends on:** —

**Context (для субагента):**
Flutter-web проект уже существует. Сейчас закладываем design-токены из неo-футуристического макета. Никакой логики не трогаем.

**Reference files (mockup, read-only):**
- `@design/reference/mockup/tokens.jsx` (`NF`, `FONT_SANS`, `FONT_MONO`)
- `@design/reference/mockup/README.md`

**Target files (Flutter project):**
- `lib/theme/colors.dart` (create)
- `lib/theme/typography.dart` (create)
- `lib/theme/radii.dart` (create)
- `lib/theme/motion.dart` (create)
- `lib/theme/shadows.dart` (create)
- `lib/theme/radar_theme.dart` (create — composes ThemeData)
- `lib/main.dart` (wire `RadarTheme.light` into `MaterialApp.theme`)

**Task:**
1. Create `NFColors` class mirroring the token table in the prompts doc. Use `const Color(0xFF0E0F0D)` literal form. For `rgba()` tokens construct via `Color.fromRGBO`.
2. Create `NFRadii` with `radius = 18`, `radiusLg = 26`, `radiusSm = 10` as `BorderRadius`.
3. Create `NFMotion` with the four named durations and curves from the tokens table.
4. Create `NFShadows.card`, `NFShadows.bottomNav`, `NFShadows.toast`, `NFShadows.tabletPanel` — each a `List<BoxShadow>`.
5. Create `NFTypography.sans` using `GoogleFonts.nunitoTextTheme` (add `google_fonts` if not present — ONLY dependency allowed in this prompt). Provide named styles matching mockup sizes actually used in screens: `display` (38/42/46, w800, letter -1.2/-1.4/-1.6), `h1` (22, w700, -0.6), `h2` (21, w600, -0.5), `body` (14.5, 1.5), `meta` (13, 600), `mono` (10, w700, uppercase, letter-spacing 0.8 → `FontFeature.tabularFigures()` + `letterSpacing: 0.8`).
6. `RadarTheme.light` composes these. No dark theme.

**Web → Flutter mapping:**
- `letter-spacing: 1.2px` → `letterSpacing: 1.2`
- `text-transform: uppercase` → call `.toUpperCase()` at widget level (not theme) OR wrap in `Text.rich` — document choice.
- `font-variation: wght N` → `FontWeight.w{N}`
- CSS `rgba(17,17,17,0.09)` → `Color.fromRGBO(17, 17, 17, 0.09)`

**Acceptance criteria:**
- [ ] `flutter analyze` is clean.
- [ ] `flutter run -d chrome` boots; app background is `#F4F5F2`.
- [ ] A smoke test renders `Text('Радар', style: NFTypography.display)` in Nunito w800 at 42px.
- [ ] Every token in the reference table has a corresponding Dart constant; no magic numbers.

**Do NOT:**
- Don't add Material 3 color scheme generation — tokens are explicit.
- Don't introduce a state-management package in this prompt.
- Don't touch existing business-logic files.

---

### Prompt 2. Responsive primitives

**Phase:** 1 · Foundation
**Depends on:** 1

**Context:**
Закладываем брейкпоинты и помощники для адаптивной вёрстки. Макет фактически фuluid — один дерево компонентов, меняется только внешняя оболочка.

**Reference files:**
- `@design/reference/mockup/radar-web.html` — функции `useLayout()`, `ResponsiveShell`.
- `@design/reference/mockup/README.md` — таблица breakpoints.

**Target files:**
- `lib/responsive/breakpoint.dart` (create)
- `lib/responsive/responsive_builder.dart` (create)
- `lib/responsive/responsive_value.dart` (create)
- `lib/responsive/context_ext.dart` (create)

**Task:**
1. `enum Breakpoint { mobile, tablet, desktop }`.
2. Thresholds from mockup: `mobileMax = 767`, `tabletMax = 1199`. Expose as static consts.
3. `extension BreakpointContext on BuildContext { Breakpoint get breakpoint; bool get isMobile/isTablet/isDesktop; }` — resolves via `MediaQuery.sizeOf(context).width`.
4. `class ResponsiveValue<T> { final T mobile; final T? tablet; final T? desktop; T resolve(BuildContext); }` — `tablet` falls back to `mobile`, `desktop` falls back to `tablet`.
5. `class ResponsiveBuilder extends StatelessWidget { final Widget Function(BuildContext, Breakpoint) builder; }` — wraps `LayoutBuilder` so nested widgets can opt into the local constraint instead of the window size. Document when to prefer each.

**Web → Flutter mapping:**
- CSS `@media (min-width: 1200px)` → `context.breakpoint == Breakpoint.desktop`
- `useLayout()` hook → `context.breakpoint` extension

**Acceptance criteria:**
- [ ] Unit test: `ResponsiveValue(mobile: 10, desktop: 20).resolve(ctx)` returns 10 / 10 / 20 at the three ranges.
- [ ] `flutter analyze` clean.
- [ ] Widget test: `ResponsiveBuilder` reports correct `Breakpoint` at widths 375, 900, 1440.

**Do NOT:**
- Don't add `flutter_screenutil` or similar — our scale is absolute, not proportional.
- Don't change `main.dart`.

---

### Prompt 3. Assets — fonts, icons, placeholder

**Phase:** 1 · Foundation
**Depends on:** 1

**Context:**
Готовим ассеты: шрифт Nunito, SVG-иконки (из `Icon` атласа в tokens.jsx), и генератор полосатого плейсхолдера вместо реальных фото.

**Reference files:**
- `@design/reference/mockup/tokens.jsx` — функция `Icon({ name })` (перечисление имён: `search, bell, thumb-up, thumb-down, bookmark, feed, layers, user, gear, back, plus, chevron, arrow-up-right, arrow-right, external, spark, close, check, filter, grid, trash, link, radar, star, more, eye-off, folder-plus`) и `Stripe` — полосатый плейсхолдер.

**Target files:**
- `assets/fonts/Nunito-*.ttf` (add files, 400/500/600/700/800/900)
- `assets/icons/*.svg` (one per icon name; source the SVG paths verbatim from `tokens.jsx` — 24x24 viewBox, stroke 1.6, `currentColor`)
- `pubspec.yaml` (register font + assets; add `flutter_svg` dependency)
- `lib/ui/atoms/nf_icon.dart` (create — thin wrapper over `SvgPicture.asset` with size + color params)
- `lib/ui/atoms/stripe_placeholder.dart` (create — `CustomPainter` that draws diagonal stripe pattern)

**Task:**
1. Extract every icon from `Icon()` switch in `tokens.jsx` into its own SVG file. Stroke attrs: `stroke-width="1.6"`, `stroke-linecap="round"`, `stroke-linejoin="round"`, `fill="none"` (or `fill="currentColor"` where the JSX sets it — e.g. `more`, `radar` dot).
2. `NFIcon(name: String, size: double = 22, color: Color?)` → picks `assets/icons/$name.svg`, applies color via `colorFilter: ColorFilter.mode(color, BlendMode.srcIn)`.
3. `StripePlaceholder` draws the pattern from `Stripe`: `repeating-linear-gradient(135deg, pair[0] 0 14px, pair[1] 14px 28px)` + radial highlight overlay. Accept `tone: StripeTone` enum with the same 8 values (ink/accent/lime/light/warn/violet/teal/rose) from JSX. Optional `label` (mono, top-left) and `seed` (mono `#NNN`, bottom-right).

**Web → Flutter mapping:**
- `repeating-linear-gradient(135deg, A 0 14px, B 14px 28px)` → `CustomPainter` that tiles two `Rect` fills at 45° (actually 135° from horizontal = use `canvas.rotate(135 * pi/180)` before drawing stripes of width 14).
- `background: radial-gradient(120% 80% at 10% 0%, white 0%, transparent 60%)` → `RadialGradient(center: Alignment(-0.8, -1), radius: 1.2, colors: [white14, transparent])` painted as an overlay rect.
- CSS `border-radius: 16px; overflow: hidden` → clip the `CustomPaint` with `ClipRRect(borderRadius: 16)`.

**Acceptance criteria:**
- [ ] `flutter run -d chrome` renders `NFIcon(name: 'gear')` identical to `gear` svg in `tokens.jsx`.
- [ ] `StripePlaceholder(tone: StripeTone.lime, label: 'ФОТО', seed: 42)` visually matches `<Stripe tone="lime" seed={42}/>` in the mockup.
- [ ] `flutter_svg` is the ONLY new dependency.
- [ ] Pubspec registers exactly six Nunito weights.

**Do NOT:**
- Don't rasterize icons to PNG.
- Don't hand-draw icons in `CustomPaint` — they must live in `assets/icons/` as SVG.

---

## Phase 2 — Atoms

### Prompt 4. Text atoms — `NFText` and `Mono`

**Phase:** 2 · Atoms
**Depends on:** 1

**Reference files:**
- `@design/reference/mockup/tokens.jsx` — `Mono` component.
- `@design/reference/mockup/cards.jsx` — usage patterns across `SourceLine`, `CardBody`, `AdCard`.

**Target files:**
- `lib/ui/atoms/nf_text.dart` (create)

**Task:**
1. `NFText.display / h1 / h2 / body / meta / mono` — named constructors over `Text` with the typography from Prompt 1.
2. `NFText.mono(String)` must uppercase the value and apply `letterSpacing: 0.8`, `fontSize: 10`, `FontWeight.w700`, `color: NFColors.mute` (overridable).
3. Support `textAlign`, `maxLines`, `overflow`, optional `color`.

**Acceptance criteria:**
- [ ] Golden test `nf_text_display.png` at mobile, tablet, desktop widths matches snapshot.
- [ ] `NFText.mono('РЕКЛАМА · BRAND')` renders uppercase, no manual `.toUpperCase()` at call site.

**Do NOT:** don't inline styles at call sites — every text style routes through `NFText`.

---

### Prompt 5. Stripe placeholder variants — single + multi

**Phase:** 2 · Atoms
**Depends on:** 3

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `SingleImage`, `MultiImage` functions.

**Target files:**
- `lib/ui/atoms/single_image.dart` (create)
- `lib/ui/atoms/multi_image.dart` (create)

**Task:**
1. `SingleImage({item, height = 200})` → `ClipRRect(radius 16) + StripePlaceholder(tone, seed, label: 'ФОТО')`.
2. `MultiImage({item, height = 200})` — grid 2-col × 2-row, `[2fr 1fr]` columns; left cell spans both rows. 4px gap. Outer radius 16, inner cells radius 0. Uses `item.tone`, `item.toneSecondary ?? accent`, `item.toneTertiary ?? lime`. Labels `1/3`, `2/3`, `3/3`, seeds `seed`, `seed+10`, `seed+20`.

**Web → Flutter mapping:**
- CSS `grid-template-columns: 2fr 1fr; grid-template-rows: 1fr 1fr` → `Row` with `Expanded(flex: 2)` + `Column` of two `Expanded(flex: 1)`.
- `gap: 4` → `SizedBox(width: 4)` / `SizedBox(height: 4)` between children.

**Acceptance criteria:**
- [ ] Golden `multi_image_default.png` matches mockup screenshot pixel-for-pixel at 390×200.
- [ ] Works inside a fluid parent — verify with `SizedBox(width: 720, height: 200)` golden.

**Do NOT:** don't use Flutter's `GridView` — fixed 3-cell layout.

---

### Prompt 6. Reaction bar (like / dislike / bookmark)

**Phase:** 2 · Atoms
**Depends on:** 1, 3

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `ReactionBar`.

**Target files:**
- `lib/ui/atoms/reaction_bar.dart` (create)

**Task:**
1. Three circular 40×40 (compact 34×34) buttons. Spacing 8.
2. Like — active fill `NFColors.accent`, icon `thumb-up` in `accentInk`.
3. Dislike — active fill `NFColors.ink`, icon in white.
4. Bookmark — active fill `NFColors.lime`, icon `bookmark` stroke AND fill `limeInk`.
5. Inactive — transparent fill, hairline border, icon in `ink2`.
6. `onTap` callbacks; pressing like unsets dislike and vice versa — behaviour lives in parent, atom just reports.
7. Tap anim: scale to 0.92 on press-down (`Curves.easeOut`, 180ms).

**Web → Flutter mapping:**
- `transition: background 160ms ease, border-color 160ms ease` → `AnimatedContainer(duration: NFMotion.fast)`.
- `e.stopPropagation()` → wrap in `GestureDetector(behavior: HitTestBehavior.opaque)` + don't bubble.

**Acceptance criteria:**
- [ ] Golden for every state combination (8 states).
- [ ] Tapping like while dislike active does NOT clear dislike inside the atom — parent owns state.

**Do NOT:** don't mutate internal state; atom is stateless.

---

### Prompt 7. Source line + icon button

**Phase:** 2 · Atoms
**Depends on:** 1, 3

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `SourceLine`, `IconBtn`.

**Target files:**
- `lib/ui/atoms/source_line.dart` (create)
- `lib/ui/atoms/icon_btn.dart` (create)

**Task:**
1. `SourceLine` — 18×18 ink square with rotated lime diamond glyph, source name (13/w600), 3px dot separator, time (mono), optional read-time (mono after another dot), optional right-side 3-dot `more` button (28×28 circle).
2. `IconBtn` — 38×38 circle, hairline border, `surface` bg, centered `NFIcon` size 17, optional warn dot badge (7×7, 1.5px surface ring).
3. Both handle `Wrap` flow — `SourceLine` wraps if source name is long.

**Acceptance criteria:**
- [ ] Golden `source_line_default.png`, `source_line_with_more.png`, `icon_btn_bell_badge.png`.

**Do NOT:** don't fetch source favicons yet — the diamond glyph is the placeholder.

---

### Prompt 8. Card shells — ShortCard, LongCard

**Phase:** 2 · Atoms
**Depends on:** 4, 5, 6, 7

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `ShortCard`, `LongCard`, `CardBody`.

**Target files:**
- `lib/ui/cards/short_card.dart` (create)
- `lib/ui/cards/long_card.dart` (create)
- `lib/ui/cards/card_body.dart` (create)

**Task:**
1. `ShortCard(item, reactState, callbacks)` — `surface` bg, `radiusLg` (26), hairline border, `NFShadows.card`. Image variant driven by `item.images` ∈ `{one, multi, none}`; `none` skips the image block entirely (no header). Padding `8` around image, `14 18 14 18` for body. Body: `SourceLine`, 21/w600/−0.5 title (clickable), 14.5/1.5 `mute` snippet, `ReactionBar` + inky pill "Читать →".
2. `LongCard` — identical chrome, but title 22/w700/−0.6, snippet clamped `maxHeight: 96` with a 40px white fade overlay; CTA label "Читать далее →".
3. All numeric values exactly as in JSX — no rounding, no "design intuition".

**Web → Flutter mapping:**
- `linear-gradient(to bottom, rgba(255,255,255,0), surface)` overlay → `Positioned.fill` with `DecoratedBox(gradient: LinearGradient(begin: topCenter, end: bottomCenter, colors: [transparent, NFColors.surface]))`, then `Align(alignment: bottomCenter, child: SizedBox(height: 40))`.
- `box-shadow: 0 1px 0 ..., 0 8px 24px -16px ...` → `[BoxShadow(offset: (0,1), blur: 0, spread: 0, color: rgba(0,0,0,0.02)), BoxShadow(offset: (0,8), blur: 24, spread: -16, color: rgba(0,0,0,0.12))]`.

**Acceptance criteria:**
- [ ] Goldens `short_card_one / short_card_multi / short_card_none / long_card.png` at mobile (375) and desktop (760) widths.
- [ ] Tapping the title invokes `onOpen` callback; tapping "Читать" invokes same; tapping a reaction does NOT bubble to `onOpen`.

**Do NOT:** don't add image loading — stripe placeholder only.

---

### Prompt 9. Ad card — subtle / card / banner

**Phase:** 2 · Atoms
**Depends on:** 4, 7

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `AdCard`.

**Target files:**
- `lib/ui/cards/ad_card.dart` (create)
- `lib/models/ad.dart` (create — brand, tagline, title, desc, cta)

**Task:**
1. Port all three styles verbatim. All surface the "РЕКЛАМА · BRAND" mono label with a 5×5 lime dot.
2. `subtle` — regular card with 4px lime strip on left, radius `radiusLg`, padding `14 14 12 18`.
3. `card` — 1.5px lime border, 88px hatched lime header (135° hatched pattern via `CustomPainter`), giant brand wordmark 34/w800/−1.2 in `limeInk` top-right, body w700 18/−0.4 + `mute` 13.5 desc + pill CTA "→".
4. `banner` — 1 row, 54×54 lime hatched square with first letter in 22/w800, title 15/w700 ellipsized, tagline 12.5 `mute` ellipsized, small close button 26×26.
5. `onHide`, `onClick` callbacks. Close button must call `onHide` and stop propagation.

**Acceptance criteria:**
- [ ] Golden `ad_subtle.png`, `ad_card.png`, `ad_banner.png` at 375 and 760 widths.
- [ ] `AdStyle.values` matches enum `{off, subtle, card, banner}`.

**Do NOT:** don't invent new ad variants.

---

### Prompt 10. Feed skeleton + empty state

**Phase:** 2 · Atoms
**Depends on:** 1, 3

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `FeedSkeleton`, `EmptyState`.

**Target files:**
- `lib/ui/atoms/feed_skeleton.dart`
- `lib/ui/atoms/empty_state.dart`

**Task:**
1. Three placeholder cards with shimmer. Shimmer = gradient sweep `-400px → 400px` over 1400ms linear infinite. Header: lime pulse dot (scale 1 → 1.6 → 1 over 1300ms ease-in-out) + "ВАША ЛЕНТА ФОРМИРУЕТСЯ..." mono.
2. `EmptyState({icon, title, desc, accent, action})` — outer card, two concentric dashed rings (16s and 10s reverse rotations), center 86×86 disk in `accent` color floating ±4px over 3s, `NFIcon` 22 centered. Title 18/w700/−0.3, desc 13.5/1.5 `mute`, optional primary pill action.

**Web → Flutter mapping:**
- `@keyframes rdrShimmer` → `AnimationController(vsync, duration: 1400ms)` driving `LinearGradient.transform: GradientRotation` OR manual `ShaderMask`.
- Dashed border → `CustomPaint` (Flutter stroke `strokeDasharray` needs a `DashedBorderPainter`). Inline the painter in the file.
- `transform: rotate(Ndeg)` looped → `RotationTransition`.

**Acceptance criteria:**
- [ ] Animations pause correctly when widget is off-screen (`TickerMode`).
- [ ] Goldens for static frame at t=0 for both widgets.

**Do NOT:** don't introduce `shimmer` package — animate manually.

---

## Phase 3 — Compositions

### Prompt 11. Bottom nav pill (mobile + tablet)

**Phase:** 3 · Compositions
**Depends on:** 3

**Reference files:**
- `@design/reference/mockup/radar-web.html` → `BottomNav` function.

**Target files:**
- `lib/ui/nav/bottom_nav.dart` (create)

**Task:**
1. 4 tabs: `feed / collections / profile / settings`. Labels: "Лента / Подборки / Профиль / Настройки".
2. Pill container 12px from left/right/bottom of parent, radius 999, `surface` bg, hairline border, `NFShadows.bottomNav`, 6px inner padding, flex row of 4 equal tabs.
3. Active tab — `ink` bg, icon white, label `lime` w700 11px.
4. Inactive — transparent bg, icon `ink2`, label `mute` w700 11px.
5. Tap animation: scale 0.92 on press (Prompt 6 helper).

**Acceptance criteria:**
- [ ] Renders correctly at mobile and tablet; hidden at desktop (used in responsive shell).
- [ ] Golden per active index (4 images).

**Do NOT:** don't add a 5th tab — spec is 4.

---

### Prompt 12. Side nav (desktop)

**Phase:** 3 · Compositions
**Depends on:** 3

**Reference files:**
- `@design/reference/mockup/radar-web.html` → `SideNav` function.

**Target files:**
- `lib/ui/nav/side_nav.dart` (create)

**Task:**
1. `width: 240`, padding `28 16`, `bg: NFColors.bg`, right hairline border, `Sticky` at top (Flutter: `Positioned.fill` with outer Row; implement as a non-scrolling column in the desktop shell).
2. Header: 32×32 logo mark (same diamond-with-ring SVG as mobile) + "Радар" wordmark 20/w800/−0.8. Padding `0 6 20 6`.
3. Same 4 tabs as bottom nav; active state `ink` bg / white fg, inactive `ink` fg / transparent bg. Hover state: 140ms ease background transition (reserved — implement `MouseRegion` wrapper).

**Acceptance criteria:**
- [ ] Golden at desktop 1440; logo + tabs pixel-match mockup.
- [ ] Tab changes update selected state without rebuilding siblings.

**Do NOT:** don't add hotkeys, badges, or user card — spec parity only.

---

### Prompt 13. Responsive shell

**Phase:** 3 · Compositions
**Depends on:** 2, 11, 12

**Reference files:**
- `@design/reference/mockup/radar-web.html` → `ResponsiveShell` + mobile/tablet/desktop branches.

**Target files:**
- `lib/ui/shell/responsive_shell.dart` (create)
- `lib/ui/shell/device_frame.dart` (create — placeholder; see Prompt 14 for detail)

**Task:**
Single widget `ResponsiveShell({Widget child, Breakpoint breakpoint, int activeTab, VoidCallback onTab, bool showBottomNav})`:
- **mobile** — centers child in `DeviceFrame`, 28px vertical padding, 16px horizontal; bottom nav overlaid absolutely 12px from container edges.
- **tablet** — `Center` → `ConstrainedBox(maxWidth: 520)` → rounded `surface` panel (radius 28, hairline border, `NFShadows.tabletPanel`) containing the child and bottom nav overlay.
- **desktop** — `Row(children: [SideNav, Expanded(child: child)])`. No bottom nav.

`showBottomNav` is respected on mobile + tablet only.

**Acceptance criteria:**
- [ ] Widget test at each breakpoint renders the correct shell.
- [ ] Tapping a side nav tab (desktop) updates state identically to tapping a bottom nav tab (mobile).
- [ ] `CardMenu` bottom sheets (Prompt 15) still overlay correctly inside the shell at all three breakpoints.

**Do NOT:** don't use `Scaffold(bottomNavigationBar)` — nav is a floating pill, must be overlaid.

---

### Prompt 14. Device frame — iPhone / Pixel / Galaxy

**Phase:** 3 · Compositions
**Depends on:** 1

**Reference files:**
- `@design/reference/mockup/device-frame.jsx` (if present) or JSX in `radar-mobile.html` head imports (look for `IOSDevice`, `PixelDevice`, `GalaxyDevice`).

**Target files:**
- `lib/ui/shell/device_frame.dart`
- `lib/ui/shell/devices/ios.dart`
- `lib/ui/shell/devices/pixel.dart`
- `lib/ui/shell/devices/galaxy.dart`

**Task:**
1. Each device: fixed bezel dimensions, radius, shadow, status bar, gesture/home indicator. Android variants share `AndroidStatus` + `AndroidGestureBar` helpers.
2. `DeviceFrame(device: DeviceKind.iphone | .pixel | .galaxy, child)` switches between them.
3. These frames are **debug-only** — used in mobile web preview but MUST be compile-excluded in release builds via `kReleaseMode` guard.

**Acceptance criteria:**
- [ ] Golden for each device kind.
- [ ] Release build does not ship device-frame assets.

**Do NOT:** don't vendor real Apple/Google trade-dress assets — primitive shapes only.

---

### Prompt 15. Sheets — CardMenu, AddToSpaceSheet, Toast

**Phase:** 3 · Compositions
**Depends on:** 1, 3, 4, 7

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `CardMenu`, `AddToSpaceSheet`, `MenuRow`.
- `@design/reference/mockup/radar-web.html` — toast in `App`.

**Target files:**
- `lib/ui/sheets/card_menu.dart`
- `lib/ui/sheets/add_to_space_sheet.dart`
- `lib/ui/overlays/toast.dart`

**Task:**
1. All overlays use `Positioned.fill` + `rgba(14,15,13,0.4)` scrim; tapping scrim closes.
2. Sheet: bottom-anchored, `surface` bg, top corners radius 24, 12/12/20 padding, slide up from 100% over 280ms `Cubic(0.2, 0.9, 0.25, 1)`. 44×4 drag handle at top (hairline color, 4/auto/12 margin).
3. `CardMenu` rows: "Добавить источник в пространство" (folder-plus icon, locked for free plan with PREMIUM chip), "Скрыть источник" (eye-off, warn color). Cancel row at bottom in `surface2`.
4. `AddToSpaceSheet`: title block (mono + 20/w700), dashed-border "Создать новое пространство" tile with lime +, then list of user collections with tone swatch 36×36 per row.
5. `Toast`: pinned bottom 90px from container, `ink` bg, white text 13/500, optional `Вернуть` button in `lime`. Slide+fade in 220ms; auto-dismiss after 2600/3400ms depending on usage (callers pass duration).

**Acceptance criteria:**
- [ ] Widget test: opening `CardMenu` locks scroll on the underlying screen.
- [ ] Golden for each sheet open state.
- [ ] Tapping Cancel or scrim fires `onClose` once only.

**Do NOT:** don't use `showModalBottomSheet` — overlays live inside the responsive shell, not the root Scaffold.

---

## Phase 4 — Screens

> Every Phase-4 prompt carries the same **Do NOT** block:
>
> - **Do NOT change** existing state providers, repositories, routers, API clients, or navigator routes.
> - **Do NOT** introduce new packages beyond what Phases 1–3 added.
> - **Only** the presentation layer (widgets, themes, layout) is in scope. If a screen's existing widget mixes logic + view, extract the logic into a sibling `*_controller.dart` / provider file **preserving 1:1 behaviour** (no new side effects).
>
> Every Phase-4 prompt's **Acceptance criteria** always includes:
>
> - [ ] Renders correctly at mobile (< 768), tablet (768–1199), desktop (≥ 1200).
> - [ ] Screen matches the JSX definition in the reference file.
> - [ ] Existing integration / functional tests pass with no modification.

### Prompt 16. Feed screen

**Phase:** 4 · Screens
**Depends on:** 8, 9, 10, 13

**Reference files:**
- `@design/reference/mockup/screens.jsx` → `FeedScreen`.
- `@design/reference/mockup/radar-mobile.html` → state wiring.
- `@design/reference/mockup/data.jsx` → `FEED`, `ADS` schemas.

**Target files:**
- `lib/screens/feed/feed_screen.dart`
- `lib/screens/feed/feed_header.dart`

**Task:**
1. `FeedHeader` sticky at top, padding `62 18 14 18` (62 covers status bar on mobile; on desktop/tablet reduce to `20 32 14 32`). Logo mark + "Радар" wordmark on the left, "Для вас" ink pill right.
2. Feed list: 14px gap, padding `4 14 120 14` (mobile) / `4 32 120 32` (tablet) / `4 0 120 0` (desktop centered to 760).
3. Ad injection: every `adFrequency` items (default 5), pull next ad from `ADS` pool, skipping hidden ads (`hiddenAds` set). Never inject first or last. Never two ads adjacent.
4. Skeleton shown when `isLoading`. Empty state when `items.isEmpty` after filters.
5. Footer: mono `◦ КОНЕЦ ЛЕНТЫ · ПОТЯНИТЕ ДЛЯ ОБНОВЛЕНИЯ ◦`.

**Web → Flutter mapping:**
- `position: sticky; top: 0` → `SliverAppBar(pinned: true)` OR manual `Stack` with scroll controller.
- JSX `useMemo` for sequence → `computed` getter / `useMemo`-equivalent in chosen state solution; no per-scroll recomputation.

**Acceptance criteria:**
- [ ] Renders correctly at all three breakpoints.
- [ ] Matches `FeedScreen` in `screens.jsx`.
- [ ] Scroll position persists across tab switches (existing behaviour from mobile app).
- [ ] Tapping "more" on a card opens `CardMenu` (Prompt 15).

---

### Prompt 17. Detail screen + related rail

**Phase:** 4 · Screens
**Depends on:** 8, 13

**Reference files:**
- `@design/reference/mockup/screens.jsx` → `DetailScreen`, `RelatedRail`.

**Target files:**
- `lib/screens/detail/detail_screen.dart`
- `lib/screens/detail/related_rail.dart`

**Task:**
1. Back button top-left (pill with `back` icon + "Назад в ленту", 8 14 8 10, hairline border).
2. Article block centered, `maxWidth: 720` on tablet+, full width on mobile.
3. Title on desktop: 46/w800/−1.6/1.05 (mockup desktop branch). Mobile: 28/w800/−1/1.1.
4. Hero image 380 tall at `radiusLg`. Skip if `item.images == 'none'`.
5. Body paragraphs: 17/1.7 `ink2`, split on `\n\n`.
6. Action bar at bottom: `ReactionBar` + "Открыть источник" pill with `external` icon.
7. `RelatedRail` — horizontal scrollable 2.5-visible card rail after the body. "Смотреть все" chip navigates to `RelatedAllScreen`.

**Acceptance criteria:** per Phase-4 template.

---

### Prompt 18. Collections screen + editor

**Phase:** 4 · Screens
**Depends on:** 13

**Reference files:**
- `@design/reference/mockup/screens.jsx` → `CollectionsScreen`, `CollectionEditorScreen`.

**Target files:**
- `lib/screens/collections/collections_screen.dart`
- `lib/screens/collections/collection_editor.dart`
- `lib/screens/collections/collection_card.dart`

**Task:**
1. Screen has 3 sections: **Системные** (Сохранённое / Понравилось / Не понравилось), **Ваши** user collections, primary CTA "Новое пространство".
2. User collection card: tone-colored header 120 tall with hatched overlay (same pattern as AdCard), giant count number 48/w800 bottom-right, title 17/w700 below, `{count} МАТЕРИАЛОВ · {sources} ИСТОЧНИКОВ` mono.
3. Tap card → edit; tap system tile → route to filtered bookmarks/likes/dislikes.
4. Editor: title input, desc input, tone picker (7 swatches = ink/accent/lime/warn/violet/teal/rose), sources picker (multi-select from `SYSTEM_SOURCES` + `customSources`), save/delete actions.

**Acceptance criteria:** per Phase-4 template.

---

### Prompt 19. Bookmarks / likes / dislikes screen

**Phase:** 4 · Screens
**Depends on:** 8, 10, 13

**Reference files:**
- `@design/reference/mockup/screens.jsx` → `BookmarksScreen` (single component, `kind` prop switches filter).

**Target files:**
- `lib/screens/bookmarks/bookmarks_screen.dart`

**Task:**
1. Single screen with `kind: 'bookmark' | 'like' | 'dislike'`; title and copy change per kind.
2. When empty (or `forceEmpty` tweak): `EmptyState` with kind-specific icon, title, description.
3. Otherwise render the filtered list using `ShortCard` / `LongCard` (no ad injection here).

**Acceptance criteria:** per Phase-4 template.

---

### Prompt 20. Profile screen

**Phase:** 4 · Screens
**Depends on:** 13

**Reference files:**
- `@design/reference/mockup/screens.jsx` → `ProfileScreen`.

**Target files:**
- `lib/screens/profile/profile_screen.dart`

**Task:**
Port ProfileScreen verbatim — header block with lime-tile avatar (first letter of email), email (24/w800), mono email subtitle, PREMIUM badge, rows linking to bookmarks/likes/dislikes/sources/plan/preferences. Only whatever rows exist in the JSX, nothing invented.

**Acceptance criteria:** per Phase-4 template. **Explicitly verify**: no "stats dashboard", no "days streak", no content that is not in `screens.jsx`.

---

### Prompt 21. Settings screen + sub-screens (Plan, Sources, Add source, My sources)

**Phase:** 4 · Screens
**Depends on:** 13, 15

**Reference files:**
- `@design/reference/mockup/screens.jsx` → `SettingsScreen`, `PlanScreen`, `SourcesScreen`, `AddSourceScreen`, `MySourcesScreen`.

**Target files:**
- `lib/screens/settings/settings_screen.dart`
- `lib/screens/settings/plan_screen.dart`
- `lib/screens/settings/sources_screen.dart`
- `lib/screens/settings/add_source_screen.dart`
- `lib/screens/settings/my_sources_screen.dart`

**Task:**
Port each screen verbatim. For free users, tapping premium-gated options routes to `PlanScreen`. Don't duplicate the `isPremium` check — centralise in `PlanGate` widget.

**Acceptance criteria:** per Phase-4 template.

---

### Prompt 22. Onboarding — Welcome + Topics

**Phase:** 4 · Screens
**Depends on:** 1, 3, 4

**Reference files:**
- `@design/reference/mockup/onboarding.jsx` → `WelcomeScreen`, `OnboardingTopics`, `Input`.
- `@design/reference/mockup/data.jsx` → `INTERESTS`.

**Target files:**
- `lib/screens/onboarding/welcome_screen.dart`
- `lib/screens/onboarding/topics_screen.dart`
- `lib/ui/atoms/nf_input.dart`

**Task:**
1. `WelcomeScreen`: brand block, email + password inputs (reuse `NFInput`), primary CTA "Продолжить", secondary "Войти как гость".
2. `TopicsScreen`: grid of chips from `INTERESTS`; `MIN_TOPICS = 3`, `MAX_TOPICS = 5`; CTA activates only when in range.
3. On desktop / tablet, onboarding renders inside the **tablet panel** (520-wide centered) regardless of actual width — focus mode.

**Acceptance criteria:** per Phase-4 template.

---

## Phase 5 — Motion

### Prompt 23. Page transitions + screen fade

**Phase:** 5 · Motion
**Depends on:** 13, and all Phase-4 screens

**Reference files:**
- `@design/reference/mockup/radar-web.html` → `@keyframes fade`, `animation: fade 260ms ease` on screen container.

**Target files:**
- `lib/ui/motion/page_transitions.dart`
- `lib/responsive/screen_fade.dart`

**Task:**
1. Custom `PageRouteBuilder` with 260ms fade + 4px rise (identical to CSS `@keyframes fade`).
2. Wrap screen swaps (tab changes, view opens) with `ScreenFade` — `AnimatedSwitcher(duration: 260ms, transitionBuilder: FadeTransition + SlideTransition(0.004))`.
3. `Detail` hero: image transitions via `Hero` tag `'feed-hero-{itemId}'` — tween curve `Curves.easeOutCubic`, duration 320ms.

**Acceptance criteria:**
- [ ] Rapid tab-switching does not drop frames on CanvasKit (check DevTools performance).
- [ ] Hero animation does not distort the stripe pattern — use `FlightShuttleBuilder` with `ClipRRect`.

---

### Prompt 24. Micro-interactions

**Phase:** 5 · Motion
**Depends on:** 6, 11, 12

**Reference files:**
- `@design/reference/mockup/cards.jsx` — `.tab-anim` CSS.

**Target files:**
- `lib/ui/motion/press_scale.dart`

**Task:**
1. `PressScale({scale: 0.92, duration: 180ms, child})` — wraps tappable atoms. Applied to: all nav tabs, all reaction buttons, all pill CTAs.
2. Implement via `GestureDetector` + `AnimatedScale`.

**Acceptance criteria:**
- [ ] Applied everywhere the JSX `.tab-anim` class appears.
- [ ] Doesn't break `InkWell` ripples on screens that use them (decide: remove ripple in favour of scale, or layer both — document choice).

---

## Phase 6 — Polish

### Prompt 25. Golden tests across breakpoints

**Phase:** 6 · Polish
**Depends on:** Phases 1–5

**Target files:**
- `test/goldens/` (new folder; one file per screen)

**Task:**
1. For every screen from Phase 4, generate goldens at mobile (375×812), tablet (900×1024), desktop (1440×900).
2. For every atom/composition with multiple states, generate a state-matrix golden.
3. Seed data from `design/reference/mockup/data.jsx` — port `FEED`, `ADS`, `INTERESTS` verbatim into `test/fixtures/mockup_seed.dart`.

**Acceptance criteria:**
- [ ] `flutter test --update-goldens` then `flutter test` passes with zero diffs.
- [ ] Goldens rendered with `ui.PlatformDispatcher.instance.platformBrightness = Brightness.light` and `textScaleFactor = 1.0`.
- [ ] CI job added: `flutter test --reporter=expanded` in `.github/workflows/`.

---

### Prompt 26. Functional regression sweep

**Phase:** 6 · Polish
**Depends on:** Phases 1–5

**Target files:**
- existing `integration_test/` files (no new tests authored — run what exists)

**Task:**
Run full integration test suite. For every failure: classify as (a) presentation-only regression — fix by adjusting the new widget to match prior behaviour, or (b) accidental logic change — revert the offending widget file and re-port the presentation layer without touching logic hooks.

**Acceptance criteria:**
- [ ] All pre-existing integration tests pass.
- [ ] Manual smoke script (provided separately by orchestrator) passes: login → onboarding → feed scroll → open detail → bookmark → back → open bookmarks → unbookmark → switch tabs.

---

## Флаги риска (Risk flags)

Эффекты из макета, которые плохо или непредсказуемо ложатся на Flutter web + CanvasKit. Каждый с рекомендуемым фоллбеком и указанием, в каком промпте всплывёт.

1. **`backdrop-filter: blur(12px)` в TopBar + sheets** — *Prompt 11, 13, 15.*
   CanvasKit поддерживает `BackdropFilter` через `ImageFilter.blur`, но на скролле внутри родительского `Stack` — стабильный drop в FPS на слабых машинах.
   **Фоллбек:** solid translucent fill `bg + 0.92 opacity`; real blur только в sheets (статичный overlay), не на sticky-панелях. Задокументировать в Prompt 11.

2. **Анимированные `@keyframes` на радаре / пульсе / шиммере** — *Prompt 10.*
   Множественные бесконечные `AnimationController` одновременно (каждая карточка skeleton + empty-state ring + pulse) могут складываться в 12+ активных тикеров. CanvasKit не рендерит вне viewport, но контроллеры продолжают тикать.
   **Фоллбек:** `TickerMode(enabled: isVisibleInViewport)` через `VisibilityDetector`. Один shared `AnimationController` на все шиммеры экрана.

3. **Nunito FOUT** — *Prompt 1, 3.*
   `google_fonts` на web делает runtime fetch. В production — пакеты шрифтов через `pubspec.yaml → fonts:` + локальные `.ttf` в `assets/fonts/`. Между загрузкой и применением — вспышка fallback.
   **Фоллбек:** precache шрифтов в `main()` через `FontLoader` до `runApp()`. Показать splash или skeleton в это время.

4. **Stripe placeholder: `repeating-linear-gradient` на 135°** — *Prompt 3, 5.*
   Точное воспроизведение диагонального паттерна 14/14 через `CustomPainter` — требует `canvas.rotate` + `canvas.save/restore`, легко ошибиться с `ClipRect` и заехать за границы.
   **Фоллбек:** сгенерировать 1 tileable PNG/WebP 56×56 (содержит 2 периода паттерна) для каждого tone и выложить как `asset` + `BoxFit.cover`. Документированная потеря — нельзя менять цвета runtime. Если важно — использовать `ColorFilter.matrix` над tileable монохромным паттерном.

5. **Многослойные `box-shadow`** (card: `0 1px 0 ..., 0 8px 24px -16px ...`) — *Prompt 8.*
   Две тени × много карточек = перегруз raster-кеша. На CanvasKit дешевле одной тени, но не бесплатно.
   **Фоллбек:** если профилирование показывает падение — заменить вторую тень на 1px hairline top (already approximates `0 1px 0`). Сохранить внешний вид, снизить cost.

6. **`text-wrap: pretty` / orphan control** — *Prompt 1, 16, 17.*
   В Flutter web нет прямого аналога. Длинные заголовки 46/w800/−1.6 получают висячие слова.
   **Фоллбек:** вручную расставлять `Text.rich` с `WidgetSpan`-ами или non-breaking spaces там, где висячие слова портят композицию. Только для hero-заголовков Detail и Feed.

7. **Hatched backgrounds** (AdCard, CollectionCard header) — *Prompt 9, 18.*
   `repeating-linear-gradient(135deg, transparent 0 12px, rgba(...) 12px 14px)` — дорого перерисовывать на каждом frame при анимации родительской карточки.
   **Фоллбек:** запечь в PNG 24×24 tileable с `BoxDecoration(image: DecorationImage(repeat: ImageRepeat.repeat))`. Tint через `ColorFilter.mode(BlendMode.srcATop)`.

8. **SVG-иконки через `flutter_svg`** — *Prompt 3.*
   На CanvasKit каждая SVG перерастеризуется в raster-кеш; 26 иконок × состояния = большой кеш. Цветовые фильтры провоцируют re-render.
   **Фоллбек:** переключить критичные (nav, reactions) на `IconData` + font-icon-pack (vector_graphics_compiler → prerendered `BlobMessage`). Менее критичные оставить как `flutter_svg`. Замер нужен в Prompt 6 и 11.

9. **Scroll-sticky headers на CanvasKit** — *Prompt 16.*
   `SliverAppBar(pinned: true)` + `BackdropFilter` частый источник jitter. Scroll bounce на trackpad работает не на всех браузерах.
   **Фоллбек:** реализовать sticky через `CustomScrollView` с `SliverPersistentHeader`; отключить pinned на mobile под CanvasKit web (нестабильно) — пересоздавать хедер на каждый tab switch без pinning.

10. **`localStorage` для state persistence** — *Prompt 16+.*
    В web проекте state persistence уже решён через shared_preferences (предполагаемо). Но `radar-mobile.html` использует `localStorage` напрямую для `reacts`, `userCollections` и пр. В Flutter все эти блобы должны сохраняться через ЕДИНЫЙ сервис — не появляется второй источник правды.
    **Фоллбек:** строгий запрет в каждом Phase-4 промпте: "persistence only through existing `AppState` / `UserPrefsRepository` — don't write directly to `SharedPreferences`".

---

## Приложение A — реальные размеры шрифтов по экранам

Извлечено из JSX:

| Контекст                    | Size / weight / letter / line | Usage                           |
|-----------------------------|-------------------------------|---------------------------------|
| Feed hero (desktop)         | 42 / 800 / −1.4 / 1.05        | "Сегодня в радаре…" (в веб-оболочке НЕТ — mock was reverted, ignore) |
| Detail title (desktop)      | 46 / 800 / −1.6 / 1.05        | `DetailScreen` на широких экранах |
| Detail title (mobile)       | 28 / 800 / −1 / 1.1           | тот же экран, мобильная версия  |
| Detail lead paragraph       | 19 / 400 / 0 / 1.45           | `mute` цвет                     |
| Detail body                 | 17 / 400 / 0 / 1.7            | `ink2`, split by `\n\n`         |
| Collection title (screen)   | 38 / 800 / −1.2 / 1.05        | "Тематические коллекции"        |
| Collection count            | 48 / 800 / −1.6 / 1            | цифра в tone-header             |
| Card title (short)          | 21 / 600 / −0.5 / 1.15        | `ShortCard`                     |
| Card title (long)           | 22 / 700 / −0.6 / 1.14        | `LongCard`                      |
| Card body snippet           | 14.5 / 400 / 0 / 1.5          | `mute`                          |
| Source line name            | 13 / 600 / −0.1 / —           | `ink`                           |
| Mono label                  | 10 / 700 / 0.8 / —            | uppercase, `mute`               |
| Bottom tab label            | 11 / 700 / 0.1 / —            | active `lime`, inactive `mute`  |
| Sheet title                 | 20 / 700 / −0.5 / 1.15        | `AddToSpaceSheet`               |
| AdCard title (subtle/card)  | 18 / 700 / −0.4 / 1.25        | `ink`                           |
| AdCard banner title         | 15 / 700 / −0.3 / 1.3         | ellipsized                      |
| Brand wordmark (AdCard card)| 34 / 800 / −1.2 / 1           | giant letters in header         |

Все значения извлечены дословно из `cards.jsx`, `screens.jsx`, `radar-web.html`. Если в будущем какой-то экран появится с новыми размерами — добавь сюда же.
