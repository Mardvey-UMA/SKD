# Радар — Responsive Mockup Reference

This folder is the **source of truth** for the Flutter-web redesign.
It is a self-contained mockup: open `radar-web.html` and everything renders — no build step, no external dependencies beyond the CDN scripts in the `<head>`.

## Entry point

- **`radar-web.html`** — the ONLY HTML entry. It contains the `ResponsiveShell` that switches between mobile / tablet / desktop layouts based on viewport width (or the `breakpoint` Tweak). Every screen and flow is reachable from here.

## Files

| File                  | What's in it                                                                                      |
|-----------------------|---------------------------------------------------------------------------------------------------|
| `radar-web.html`      | Entry HTML + `ResponsiveShell` + nav (`BottomNav`, `SideNav`) + state wiring for the app.        |
| `tokens.jsx`          | Design tokens: `NF` colors, `FONT_SANS`/`FONT_MONO`, `Icon` atlas (26 icons), `Stripe` placeholder, `Mono` label atom. |
| `cards.jsx`           | Card/sheet atoms: `ShortCard`, `LongCard`, `AdCard`, `ReactionBar`, `SourceLine`, `FeedHeader`, `CardMenu`, `AddToSpaceSheet`, `FeedSkeleton`, `EmptyState`, `IconBtn`. |
| `screens.jsx`         | Every screen: `FeedScreen`, `DetailScreen`, `RelatedAllScreen`, `CollectionsScreen`, `CollectionEditorScreen`, `BookmarksScreen`, `ProfileScreen`, `SettingsScreen`, `PlanScreen`, `SourcesScreen`, `AddSourceScreen`, `MySourcesScreen`. |
| `onboarding.jsx`      | `WelcomeScreen`, `OnboardingTopics`, `Input`.                                                     |
| `data.jsx`            | Seed content: `FEED`, `ADS`, `SYSTEM_SOURCES`, `INTERESTS`, `FULL_BODY`.                          |
| `device-frame.jsx`    | `DeviceFrame` wrapper — chooses iPhone / Pixel / Galaxy shell.                                    |
| `ios-frame.jsx`       | iPhone bezel (`IOSDevice`) with status bar + home indicator.                                      |
| `android-frame.jsx`   | Pixel / Galaxy bezels.                                                                            |

## Responsive strategy

The mockup is **fluid**, not three separate per-breakpoint trees. Screen components are breakpoint-agnostic; only the outer shell changes.

| Breakpoint | Threshold     | Shell                                          | Primary nav        | Ref in `radar-web.html`      |
|------------|---------------|------------------------------------------------|--------------------|------------------------------|
| mobile     | `< 768px`     | `DeviceFrame` (iPhone / Pixel / Galaxy)        | bottom pill nav    | `ResponsiveShell`, `mobile` branch  |
| tablet     | `768–1199px`  | 520-wide rounded panel centered, no device frame | bottom pill nav  | `ResponsiveShell`, `tablet` branch  |
| desktop    | `≥ 1200px`    | full-bleed, 240px left `SideNav` + main column | left sidebar       | `ResponsiveShell`, `desktop` branch |

The screens themselves are byte-identical across breakpoints — only their container changes.

## Tweaks (runtime knobs)

The HTML ships with a Tweaks panel (toolbar toggle). Defaults live in `radar-web.html` between `/*EDITMODE-BEGIN*/` and `/*EDITMODE-END*/` as valid JSON:

- `accent`, `secondary` — color tokens.
- `plan` — `free` / `premium` (gates premium features).
- `device` — `iphone` / `pixel` / `galaxy` (mobile only).
- `breakpoint` — `fluid` / `mobile` / `tablet` / `desktop` (force a layout regardless of window width).
- `forceLoading` — show `FeedSkeleton` in the feed.
- `emptyBookmarks` / `emptyLikes` / `emptyDislikes` — show `EmptyState` on those screens.
- `adStyle` — `off` / `subtle` / `card` / `banner`.
- `adFrequency` — inject an ad every N items.

## How to use this as a Flutter redesign reference

1. Drop this folder (`mockup/`) into your Flutter project at `design/reference/mockup/`.
2. Read `design/reference/radar-redesign-prompts.md` (lives one level up). It lists 26 prompts across 6 phases that recreate this mockup in Flutter web (CanvasKit).
3. Every prompt references files here by path (e.g. `@design/reference/mockup/cards.jsx`) — keep paths stable.
