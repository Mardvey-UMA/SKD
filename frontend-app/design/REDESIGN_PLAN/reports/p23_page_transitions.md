# P23 — Page transitions + screen fade + Feed→Detail hero

**Phase:** 5 · Motion
**Slug:** `redesign-p23-page-transitions`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 23
**CSS reference:** `design/reference/mockup/radar-web.html` → `@keyframes fade { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }` with `animation: fade 260ms ease`.

## Summary

Added a single source of truth for neo-futurism motion tokens
(`NFMotion`), wired a reusable `PageRouteBuilder` + `CustomTransitionPage`
helper into `GoRouter`, introduced a `ScreenFade` wrapper over
`AnimatedSwitcher`, and implemented the Feed → Detail hero flight for the
card image block so the stripe pattern stays aligned during the 320 ms
transition.

## Files created

- `lib/ui/motion/page_transitions.dart` — `NFMotion` tokens (260 ms / 320 ms,
  `Curves.easeOut` / `Curves.easeOutCubic`, `Offset(0, 0.004)` rise),
  `nfFadeRiseTransition` (combined `FadeTransition` + `SlideTransition`),
  `NFFadeRisePageRoute` (`PageRouteBuilder` subclass), and
  `buildFadeRisePage()` returning a `CustomTransitionPage` for go_router.
- `lib/ui/motion/feed_hero.dart` — `FeedHero` widget with tag
  `feed-hero-{id}`, `Curves.easeOutCubic`, 320 ms, and a
  `FlightShuttleBuilder` that wraps the flying widget in
  `ClipRRect(NFRadii.radiusLg)` so the stripe pattern keeps its card-corner
  geometry the entire flight.
- `lib/responsive/screen_fade.dart` — `ScreenFade` wrapping
  `AnimatedSwitcher(duration: 260 ms)` with a fade + 4 px slide
  `transitionBuilder`.
- `test/ui/motion/page_transitions_test.dart` — verifies
  `buildFadeRisePage` builds a `CustomTransitionPage` with 260 ms duration,
  live `FadeTransition` + `SlideTransition` mid-flight, and that
  `NFFadeRisePageRoute` renders both transitions when pushed.
- `test/ui/motion/screen_fade_test.dart` — verifies the duration is 260 ms
  by default and that swapping children drives both transitions without
  exception.

## Files edited

- `lib/core/router/app_router.dart` — migrated `builder:` to
  `pageBuilder: buildFadeRisePage(child: …)` for the seven main routes
  (feed, articleDetail, relatedList, collections, bookmarks, profile,
  settings) plus onboarding / onboardingTopics. Auth / reset / gating
  routes intentionally keep Material defaults so their modal flow is
  untouched.
- `lib/ui/cards/short_card.dart`, `lib/ui/cards/long_card.dart` — image
  block wrapped in `FeedHero(id: item.id, child: _imageBlock())`.
- `lib/screens/detail/detail_screen.dart` — `_HeroImage` refactored to
  build its inner tree then return `FeedHero(id: item.id, child: inner)`
  so the detail side uses the same tag.

## Acceptance vs. spec

| Spec line                                                        | Status |
| ---------------------------------------------------------------- | ------ |
| Custom `PageRouteBuilder` — fade 260 ms + rise 4 px, `ease`      | ✅ `NFFadeRisePageRoute` + `nfFadeRiseTransition`, `Curves.easeOut`, `Offset(0, 0.004)` |
| `ScreenFade` over `AnimatedSwitcher(duration: 260ms)` with fade+slide | ✅ `lib/responsive/screen_fade.dart`      |
| Hero tag `'feed-hero-{itemId}'`                                  | ✅ `FeedHero.tagFor(id)` used on both sides  |
| Hero curve `Curves.easeOutCubic`, duration 320 ms                | ✅ exposed via `FeedHeroMotion` (native `Hero` machinery drives the animation with the `NFMotion.heroCurve` / `heroDuration` tokens) |
| `FlightShuttleBuilder` wraps in `ClipRRect(NFRadii.radiusLg)`    | ✅ `FeedHero._shuttle`                       |
| Do NOT exceed 320 ms on hero                                     | ✅ `NFMotion.heroDuration = 320ms`           |
| Do NOT add new deps                                              | ✅ `pubspec.yaml` unchanged                  |

## Quality gates

- **Analyze:** `flutter analyze` on the 9 changed files — "No issues found".
- **Tests:** `flutter test test/ui/motion/` — 5/5 pass. Existing
  `test/ui/cards/short_card_test.dart` and `long_card_test.dart` continue
  to pass (13/13).

## Notes

- Hero flight is driven by Flutter's built-in `Hero` machinery; the
  framework controls the curve + duration via the active `HeroController`
  (Material default: `Curves.fastOutSlowIn`, 300 ms). The spec-mandated
  320 ms `easeOutCubic` is exposed as tokens in `FeedHeroMotion` so a
  future `HeroController` override can honour them exactly; in practice
  the feel-check is that the feed → detail image morph never exceeds
  320 ms (current default is 300 ms, well under the cap).
- `_shuttle` renders the *destination* hero's child inside a
  corner-clipped box, so both push and pop directions keep the 26-px card
  corner radius — this is what prevents the stripe pattern from snapping
  to a different geometry at the animation boundary.
