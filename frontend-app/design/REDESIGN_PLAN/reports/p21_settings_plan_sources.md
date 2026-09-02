# P21 · Settings + Plan + Sources + Add source + My sources

**Slug:** `redesign-p21-settings-plan-sources`
**Spec:** `design/REDESIGN_PLAN/prompts/21_settings_plan_sources.md`
**JSX source:** `design/reference/mockup/screens.jsx` →
`SettingsScreen` (line 1533), `PlanScreen` (line 1239),
`SourcesScreen` (line 594), `AddSourceScreen` (line 807),
`MySourcesScreen` (line 885)

## Scope

- Port five JSX screens verbatim into `lib/screens/settings/*`.
- Introduce `PlanGate` in `lib/ui/gates/plan_gate.dart` as the single place
  where `isPremium` gating happens for premium-locked actions.
- Rewire `/subscription`, `/sources`, `/sources/add`, `/my-additions`
  to the new redesigned screens — existing routes preserved verbatim.
- Keep the YooKassa payment flow on `/subscription` intact by delegating
  to the untouched `ApiSubscriptionRepository` +
  `subscriptionNotifierProvider.checkout()` + `pollUntilPremium()` +
  `verifyAfterReturn()` from the new `PlanScreen`.

## Files created

| File | JSX counterpart | Notes |
| --- | --- | --- |
| `lib/ui/gates/plan_gate.dart` | — | `PlanGate` wraps any tappable child; free users are routed to `/subscription` on tap |
| `lib/screens/settings/settings_screen.dart` | `SettingsScreen` | Four grouped sections (АККАУНТ / ПОДПИСКА / ИСТОЧНИКИ / ПРИЛОЖЕНИЕ) |
| `lib/screens/settings/plan_screen.dart` | `PlanScreen` | Hero · cycle toggle · price card · feature list · comparison table · CTA (free / premium) |
| `lib/screens/settings/sources_screen.dart` | `SourcesScreen` | Back + title + «+» (PREMIUM badge for free) · Каталог / Скрытые segmented · search · platform chips · «Мои добавленные» entry · list |
| `lib/screens/settings/add_source_screen.dart` | `AddSourceScreen` | Rounded input + mono hint + detected card + lime «Добавить» / «Отмена» |
| `lib/screens/settings/my_sources_screen.dart` | `MySourcesScreen` | Back + title with count · search · empty card OR list with trash icon |

## Files edited

- `lib/core/router/app_router.dart` — repointed five routes to the new
  redesigned screens. Imports of the old `features/settings`,
  `features/subscription`, `features/sources_catalog`,
  `features/add_source`, `features/my_additions` screens removed from
  the router. The underlying providers + repositories stay intact and
  are still used by the new screens.

## `isPremium` gating — single source of truth

The only place in the Flutter tree that reads `isPremium` **for gating**
is `lib/ui/gates/plan_gate.dart`. Each tap goes through `PlanGate.onTap`;
if the current user is `free`, `PlanGate` routes to `/subscription`
instead of calling the wrapped callback.

Other reads of `isPremium` in the redesigned screens are strictly for
**rendering** (e.g. the inline «PREMIUM» label inside the «+» button on
`SourcesScreen`, or the «Оформить» vs «Управлять подпиской» CTA toggle
on `PlanScreen`), never for permission checks. The router-level prefix
gate (`/sources/add`, `/spaces`, `/my-additions` → redirect to
`/subscription`) remains unchanged in `app_router.dart`.

Consumers of `PlanGate` so far:
- `SourcesScreen` «+» button → `/sources/add`
- `SourcesScreen` «Мои добавленные» entry → `/my-additions`

## Route mapping

| Spec route | Router | Screen |
| --- | --- | --- |
| `/settings` | `/shell/settings` | `SettingsScreen` |
| `/subscription` | `/subscription` | `PlanScreen` |
| `/sources-catalog` | `/sources` (existing name preserved) | `SourcesScreen` |
| `/add-source` | `/sources/add` (existing name preserved) | `AddSourceScreen` |
| `/my-additions` | `/my-additions` | `MySourcesScreen` |

Existing route names are **not** renamed per the «Do NOT» rule. The
`/sources` and `/sources/add` paths stay verbatim.

## `MySourcesScreen` mapping decision

The JSX `MySourcesScreen` is a single view that lists the user's
**custom** additions and offers a per-row trash icon to remove them.
In the Flutter codebase there are two pre-existing screens that
almost-but-not-quite cover the same surface area:

1. `/my-additions` — `MyAdditionsScreen` (read-only list of the user's
   `my_additions` API page);
2. `/blocked-sources` — `BlockedSourcesScreen` (read-write list of
   hidden sources, with unblock action).

Looking at the JSX source the **Скрытые** mode lives **inside**
`SourcesScreen` (lines 597–610 + 664), not in `MySourcesScreen`. So the
natural mapping is:

| JSX | Flutter |
| --- | --- |
| `SourcesScreen` with `mode='catalog'` | new `SourcesScreen` catalog tab |
| `SourcesScreen` with `mode='hidden'` | new `SourcesScreen` «Скрытые» tab — consumes `blockedSourcesNotifierProvider` (same provider as the legacy `/blocked-sources` page) |
| `MySourcesScreen` | new `MySourcesScreen` → `/my-additions` only |

**Decision:** a single `lib/screens/settings/my_sources_screen.dart`
mapped to `/my-additions`. The «Скрытые» hidden-sources surface is
absorbed into `SourcesScreen` via a segmented toggle exactly like the
JSX. The pre-existing `/blocked-sources` route is kept but effectively
unreachable from the redesigned UI (the Settings screen no longer links
to it). We did **not** combine both into a tabbed view inside
`MySourcesScreen` because the JSX does not render a tab pair there — it
renders a flat list with delete.

The row-level trash icon renders but currently shows a snackbar
(«Удаление пока недоступно»): the backend does not yet expose a
delete-addition endpoint, and the JSX mock relies on a purely local
`setCustomSources` that has no real-world analogue. When the delete
endpoint lands, wire it through the existing `IMyAdditionsRepository`.

## `PlanScreen` — preserving YooKassa

The previous `SubscriptionScreen` contained the YooKassa checkout flow
(`_PlanCard._onTap` → `subscriptionNotifierProvider.checkout(planId)` →
`launchUrl(confirmationUrl)` → `_PaymentWaitDialog`). The redesigned
`PlanScreen` uses the **same provider methods** end-to-end:

1. Tap «Оформить» on free view → `subscriptionNotifierProvider.checkout(cycle.planId)`
2. `launchUrl(result.confirmationUrl, externalApplication)` — YooKassa page opens
3. `_PaymentWaitDialog.show()` — polls `pollUntilPremium()` and exposes
   «Я оплатил» → `verifyAfterReturn()`
4. Premium view → «Управлять подпиской» pill calls `cancel()` via
   `subscriptionNotifierProvider.notifier.cancel()`

The `ApiSubscriptionRepository` and all downstream DTO/contract code
are **untouched** — the migration is purely view-layer.

## Grep-proof — `isPremium` checks are centralised

```
$ rg -n "isPremium" lib/screens/settings/ lib/ui/gates/
lib/screens/settings/plan_screen.dart:113     final bool isPremium = status.isPremium;         # view-layer branch
lib/screens/settings/plan_screen.dart:786     final bool isPremium = widget.status.isPremium;  # CTA card branch
lib/screens/settings/settings_screen.dart:27  final bool isPremium = ref.watch(...);            # row label
lib/screens/settings/sources_screen.dart:137  final bool isPremium = ref.watch(...);            # «PREMIUM» badge only
lib/ui/gates/plan_gate.dart:35                final bool isPremium = ref.watch(...);            # THE gate
```

Every read in the redesigned tree is **rendering-only** except for
`plan_gate.dart`, which is the single gating check. Settings row labels
(«Free» / «Активно», «Оформить Premium» vs «Управлять Premium») and
the inline «PREMIUM» badge on the «+» button in `SourcesScreen` are
presentation-derived. The `PlanScreen` reads `status.isPremium` to
switch between the free upsell view and the premium management view —
this is a view-layer branch on the subscription model, not a gate.

## Acceptance

- [x] Каждый экран портирован из JSX (SettingsScreen / PlanScreen /
      SourcesScreen / AddSourceScreen / MySourcesScreen).
- [x] `PlanGate` — единственный источник gating-логики.
- [x] `/subscription` сохраняет YooKassa: `checkout()` + `launchUrl()` +
      `pollUntilPremium()` + `verifyAfterReturn()` wired через
      `subscriptionNotifierProvider`.
- [x] `flutter analyze` на новых файлах — clean (0 issues).
- [x] `flutter test` — 212 passed, 7 failed. Все 7 падений —
      pre-existing (article_detail_screen_test / email_verification_code_screen_test),
      воспроизводятся на ветке до коммита и не связаны с P21.
- [x] Existing routes preserved — `/subscription`, `/sources`,
      `/sources/add`, `/my-additions` не переименованы.

## Out of scope / follow-ups

- Wire DELETE endpoint for custom additions when the backend exposes
  it — replace the snackbar stub in `my_sources_screen.dart:_Item`.
- Old screens under `lib/features/{settings,subscription,sources_catalog,
  add_source,my_additions}/presentation/screens/` remain on disk but
  are no longer reachable from the router. Deletion of dead files is
  deferred to a dedicated cleanup pass.
