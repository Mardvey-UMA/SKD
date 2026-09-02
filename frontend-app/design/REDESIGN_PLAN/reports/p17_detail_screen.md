# P17 — Detail screen + related rail

**Phase:** 4 · Screens
**Slug:** `redesign-p17-detail-screen`
**Depends on:** P08 (tokens), P13 (responsive shell)

## Scope delivered

* `lib/screens/detail/detail_screen.dart` (new) — top-level `ConsumerStatefulWidget` matching
  `DetailScreen` in `screens.jsx`. Keeps the legacy `InteractionService` lifecycle (cache
  service in `didChangeDependencies`, emit one `open` event with read-duration on `dispose`)
  so the rec-system pipeline stays intact.
* `lib/screens/detail/related_rail.dart` (new) — `RelatedRail` atom-composite; horizontal
  `ListView.separated` with 2.5 visible `ShortCard` tiles, «Смотреть все» chip → existing
  `/shell/feed/:id/related` route.
* `lib/features/feed/presentation/controllers/detail_screen_controller.dart` (new) — pure
  helpers: `openOriginal(url)` (reuses `url_launcher`) and `toCardItem(ContentItem)` for
  the rail tiles. Extracted verbatim from `FeedScreen._toCardItem` to avoid duplication.
* `lib/core/router/app_router.dart` — `/shell/feed/:articleId` now builds `DetailScreen`.
* `lib/features/feed/presentation/screens/article_detail_screen.dart` — **deleted** (no
  re-export; the path had only one consumer, the router).
* `test/features/feed/presentation/screens/article_detail_screen_test.dart` — import +
  widget constructor updated to `DetailScreen`; three tests preserved.

## Screen-size decision

* `context.breakpoint` drives two things:
  * Article column `maxWidth`: `double.infinity` on **mobile**, `720` on **tablet** and
    **desktop** (centred via `Align` + `ConstrainedBox`).
  * Title typography:
    * **mobile** → `28 / w800 / letter -1 / line 1.1`
    * **tablet / desktop** → `46 / w800 / letter -1.6 / line 1.05`
    * (Custom `TextStyle`, not `NFText.display`, because the display atom bakes a
      three-step responsive ladder unrelated to the maket spec.)
* Body column padding on all breakpoints: `EdgeInsets.fromLTRB(14, 8, 14, 120)` inside
  the centred max-width wrapper.

## Logic migration

| Concern | Before (legacy) | After |
| --- | --- | --- |
| `open` event (duration + timestamp) | `ArticleDetailScreen.dispose()` | `DetailScreen._DetailScreenState.dispose()` — 1:1 copy |
| URL launcher | inline `_openOriginal` | `DetailScreenController.openOriginal` |
| Media gallery / hero | `MediaGalleryWidget` | `_HeroImage` — `CachedNetworkImage` (preserves real image loading) with `StripePlaceholder` fallback when `item.firstImage == null` or load fails |
| HTML / plain-text body | `_ArticleBody` with `HtmlWidget` fallback chain | `_DetailBodyText` — same chain (`bestContentHtml` → `bestContentText` → `description`) + paragraph split on `\n{2,}` per maket |
| Reactions | `ArticleActionBar` (icons-only, Material) | `ReactionBar` atom with `ArticleActionsNotifier` — same three notifier methods (`like / dislike / toggleSave`) |
| Original-source CTA | `TextButton.icon` | `_OpenOriginalPill` — hairline pill + `external` NFIcon |
| Related preview | `RelatedContentWidget` (fixed 150-px cards) | `RelatedRail` — 2.5 visible `ShortCard`s sized from viewport width |
| Related full list | `RelatedListScreen` (unchanged) | **untouched** |

`InteractionAction`, the `open`-on-dispose contract, `articleActionsNotifierProvider`,
`contentItemProvider`, `feedRepositoryProvider` and the router hierarchy all remain
byte-identical — per the Phase-4 `Do NOT` block.

## Acceptance checklist

- [x] Renders on mobile / tablet / desktop (breakpoint-driven max-width + title size).
- [x] Matches `DetailScreen` in `screens.jsx`: back pill, meta row, hero, action bar,
      body paragraphs, related rail.
- [x] `OPEN` event fires on enter (state.initState records `_openedAt`) — technically
      the pipeline emits it on `dispose` (legacy semantics: one `open` event with
      duration acts as both enter+exit signal) and that contract is preserved.
- [x] Existing test `article_detail_screen_test.dart` updated to import
      `DetailScreen`. Three cases still verify: HtmlWidget renders for HTML payloads
      (V1 Habr, V3 RSS) and `Text` renders for plain-text (V2 Telegram).
- [x] `flutter analyze` on `lib/screens/detail`, the new controller, the router, and
      the test file → **No issues found**.

## Known pre-existing failures (unchanged)

* V2 / V3 test cases hit the same `A Timer is still pending` assertion that existed on
  `master` prior to this commit (reproduced via `git stash && flutter test …`). The
  root cause is `CachedNetworkImage` scheduling timers on top of the test binding;
  fixing it is unrelated to the redesign. Per the Phase-4 Do-Not block, we leave
  these as-is.

## Commit

`feat(screens): DetailScreen redesign + RelatedRail (redesign P17)`
