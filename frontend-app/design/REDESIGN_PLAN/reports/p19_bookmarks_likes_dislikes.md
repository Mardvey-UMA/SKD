# P19 Report — Bookmarks / likes / dislikes screen

**Slug:** `redesign-p19-bookmarks-likes-dislikes`
**Depends on:** P08 (NFIcon), P10 (EmptyState), P13 (ShortCard / LongCard)

## Summary

Single screen `BookmarksScreen` with `BookmarksKind { bookmark, like, dislike }`
replaces the legacy tri-tabbed `CollectionsScreen` at `/shell/bookmarks`. Header
mirrors the JSX `BookmarksScreen` — 38 × 38 round pill back button +
`MONO: count` kicker + 28 / w700 / -0.9 Nunito title — and the body either shows
the kind-specific `EmptyState` (P10) or a `ShortCard` / `LongCard` list driven
by the existing `features/collections` providers. **No ad-injection** is applied
here (per spec).

## Files

- **Created:** `lib/screens/bookmarks/bookmarks_screen.dart`
- **Edited:** `lib/core/router/app_router.dart`
  - Removed legacy import alias `bookmarks_legacy`
  - Added `lib/screens/bookmarks/bookmarks_screen.dart` import
  - `/shell/bookmarks` GoRoute now builds `BookmarksScreen(kind: …)` via the
    existing `?kind=bookmark|like|dislike` query param; the `/shell/` premium /
    auth guards defined higher up in the `redirect` callback still apply.

The legacy `lib/features/collections/presentation/screens/collections_screen.dart`
file is left on disk but is no longer imported anywhere in `lib/`. Unchanged:
`ApiCollectionsRepository`, `bookmarksNotifierProvider`, `likesNotifierProvider`,
`dislikesNotifierProvider`, `ArticleActionsNotifier`, and all DTOs.

## Design mapping (JSX → Flutter)

| JSX (screens.jsx §`BookmarksScreen`)     | Flutter                                                    |
| ---------------------------------------- | ---------------------------------------------------------- |
| `kind` prop with `KINDS[kind]` lookup    | `BookmarksKind` enum + `_KindMeta.forKind(kind)`           |
| Title 28 / w700 / -0.9 Nunito            | `Text(style: …)` literal, matches tokens 1:1               |
| `Mono` kicker `{kicker}: {count}`        | `NFText.mono('${meta.kicker}: $count')`                    |
| Round 38 × 38 surface-tinted back button | `Container + NFIcon('back')` with `BoxShape.circle`        |
| `EmptyState icon accent title desc`      | `EmptyState(iconName, accent, title, desc)` from P10       |
| `FEED.filter(f => reacts[f.id]?.[kind])` | Existing `bookmarks/likes/dislikes` providers (server-side) |
| `ShortCard` / `LongCard` per `item.kind` | `_isLongForm()` heuristic → `LongCard` else `ShortCard`    |

### Kind metadata

| kind       | title             | kicker      | icon          | accent                    | empty title | empty desc                        |
| ---------- | ----------------- | ----------- | ------------- | ------------------------- | ----------- | --------------------------------- |
| `bookmark` | «Сохранённое»     | `СОХРАНЕНО` | `bookmark`    | `EmptyStateAccent.lime`   | «Пусто»     | «Сохранённое покажется здесь»     |
| `like`     | «Понравилось»     | `ОДОБРЕНО`  | `thumb-up`    | `EmptyStateAccent.accent` | «Пусто»     | «Понравившееся покажется здесь»   |
| `dislike`  | «Не понравилось»  | `СКРЫТО`    | `thumb-down`  | `EmptyStateAccent.ink`    | «Пусто»     | «Скрытое покажется здесь»         |

## Behaviour preserved

- **Pagination**: `ScrollController` triggers the kind-specific `loadMore()`
  when within 240 px of the bottom — identical semantics to the legacy tabbed
  screen.
- **Pull-to-refresh**: `RefreshIndicator` wraps the `CustomScrollView` and
  calls the kind-specific `refresh()`.
- **Error state**: surfaces a «Повторить» `TextButton` that re-runs `refresh()`.
- **Open → detail**: tapping the title / CTA emits an `InteractionEvent` with
  `action=open` (matching the pre-redesign `FeedCard` behaviour) and pushes
  `/shell/feed/{id}`.
- **Reactions**: like / dislike / bookmark go through the shared
  `ArticleActionsNotifier`, so the same cache drives this screen, the feed, the
  detail screen, and the related-articles rail.
- **Back**: `canPop()` → `context.pop()`, else `context.go('/shell/collections')`
  so deep-linking via `/shell/bookmarks?kind=like` from outside the shell still
  returns to a sensible place.

## Non-goals

- No ad-injection (explicit spec requirement).
- No changes to `CollectionsRepository` / `ApiCollectionsRepository`.
- No new packages.

## Validation

- `flutter analyze lib/screens/bookmarks/bookmarks_screen.dart lib/core/router/app_router.dart`
  → **No issues found** (1.0 s).
- `flutter test` → 210 passed, 7 pre-existing failures in
  `test/features/feed/presentation/screens/article_detail_screen_test.dart`
  (verified against `master` before the change — identical failures, unrelated
  to P19).

## Acceptance criteria

- [x] One widget with `kind` enum drives title / kicker / empty copy / icon /
      accent / source provider.
- [x] Empty state uses P10 `EmptyState` with kind-specific icon and accent.
- [x] Non-empty list renders `ShortCard` / `LongCard` via heuristic, no ads.
- [x] `ApiCollectionsRepository` / collections providers untouched.
- [x] Route `/shell/bookmarks?kind=…` points to new screen with correct kind,
      existing auth / onboarding / premium guards preserved.
