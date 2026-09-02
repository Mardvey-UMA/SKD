# Prompt 13 — Responsive shell — Report

**Date:** 2026-04-21
**Slug:** `redesign-p13-responsive-shell`
**Phase:** 3 · Compositions

## Files

### Created

- `lib/ui/shell/responsive_shell.dart` — `ResponsiveShell({child, bp, activeTab, onTab, showBottomNav})`. Switches between three branches by `Breakpoint`:
  - **mobile (< 768)** — `ColoredBox(NF.bg)` → `Padding(28v/16h)` → `Center` → `DeviceFrame(390×844)` → `Stack([child, Positioned(BottomNav, 12px from edges)])`.
  - **tablet (768–1199)** — `Padding(24v/16h)` → `Center` → `ConstrainedBox(maxWidth: 520)` → surface panel (radius 28, hairline, `NFShadows.tabletPanel`) → `ClipRRect` → `Stack([child, Positioned(BottomNav, 12px from edges)])`.
  - **desktop (≥ 1200)** — `Row([SideNav, Expanded(child)])`, no BottomNav.
  - `showBottomNav` only respected on mobile/tablet.
- `lib/ui/shell/device_frame.dart` — minimal placeholder for Prompt 14. Fills the expected 390×844 rounded surface with hairline + `NFShadows.tabletPanel`. Prompt 14 will expand into real iOS / Pixel / Galaxy variants.
- `test/ui/shell/responsive_shell_test.dart` — 5 widget tests covering 375/900/1440 breakpoints, `showBottomNav=false`, and `onTab` → `activeTab` rebuild path.

### Edited

- `lib/app.dart` — **Removed** the `PhoneAspectRatio` wrapper (class and builder). `MaterialApp.router` now passes directly to the GoRouter config; responsive layout is owned entirely by `ResponsiveShell`.
- `lib/core/router/app_router.dart` — Replaced the `StatefulShellRoute.indexedStack(builder: MainShellScreen)` with a `ShellRoute` whose builder wires `ResponsiveShell(bp: context.breakpoint, activeTab: _indexFrom(state.uri), onTab: _goTab, child: child)`. Added private helpers `_indexFrom(Uri)` (maps `/shell/feed → 0`, `/shell/collections` or `/spaces → 1`, `/shell/profile → 2`, `/shell/settings → 3`) and `_goTab(ctx, i)` (`context.go('/shell/...')`). Child routes (`FeedScreen`, `ArticleDetailScreen`, `RelatedListScreen`, `CollectionsScreen`, `ProfileScreen`, `SettingsScreen`) are preserved 1:1 on their original paths.

### Untouched

- `lib/features/shell/presentation/screens/main_shell_screen.dart` is left in place (spec: «Не удалять код существующего `main_shell_screen.dart`»), but no longer imported. Kept as a historical artefact in case a future StatefulShell variant is revisited.
- `lib/features/shell/presentation/widgets/glass_bottom_nav.dart` is untouched for the same reason.

## Auth-guard decision

**Decision:** auth-guard is kept **entirely in the router `redirect` callback** (`lib/core/router/app_router.dart:61-133`). The old `main_shell_screen.dart` never held any auth logic — it was a purely visual shell (`Scaffold + GlassBottomNav`). Therefore the switch from `StatefulShellRoute → ShellRoute` has **zero impact** on the auth/onboarding/premium redirect pipeline:

- `isAuthenticated`, `isPendingCodeVerification`, `hasCompletedOnboarding` checks run in `redirect` and fire on every navigation.
- Premium-tier gate for `/sources/add`, `/spaces/**`, `/my-additions` remains intact.
- `refreshListenable` wiring on `authNotifierProvider`, `onboardingStatusProvider`, and `subscriptionNotifierProvider` is preserved verbatim.

Trade-off: `ShellRoute` (unlike `StatefulShellRoute.indexedStack`) does **not** preserve per-branch navigation stacks — e.g. tapping from `feed → article_detail → related`, then switching to `collections` and back, now returns the user to the feed root. This matches the web mockup's behaviour (single location-driven `setActive(tab)` call in `radar-web.html` § App), so it is accepted here. If per-tab stack preservation becomes a requirement later, reintroduce `StatefulShellRoute` with `ResponsiveShell` wrapping the `navigationShell` child (no API break).

## Acceptance criteria

- [x] Widget-test per breakpoint (375, 900, 1440) picks the correct branch.
- [x] `onTab` callback propagates to `activeTab` on rebuild (tap path verified).
- [x] `BottomNav` positioning (12px from edges, absolute overlay) — no `Scaffold.bottomNavigationBar` used.
- [x] Desktop: `SideNav` always visible, no `BottomNav`.
- [x] Existing route paths and redirect logic unchanged.
- [x] `flutter analyze lib/ui/shell/ lib/app.dart lib/core/router/app_router.dart test/ui/shell/` → `No issues found!`.
- [x] All existing tests that previously passed still pass. The 7 pre-existing failures in `test/features/auth/presentation/screens/email_verification_code_screen_test.dart` and `test/features/auth/presentation/widgets/otp_input_field_test.dart` were verified present on `master` (commit `c38bb23`) before any P13 changes; they are unrelated to this prompt.

## Next

- Prompt 14 expands `DeviceFrame` into device-specific variants (iOS status bar, Pixel cut-out, Galaxy hole-punch).
- Prompt 15 adds `CardMenu` — acceptance criterion «CardMenu overlays correctly over shell» will be validated there.
