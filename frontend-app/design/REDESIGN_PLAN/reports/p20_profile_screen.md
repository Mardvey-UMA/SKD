# P20 · Profile screen

**Slug:** `redesign-p20-profile-screen`
**Spec:** `design/REDESIGN_PLAN/prompts/20_profile_screen.md`
**JSX source:** `design/reference/mockup/screens.jsx` → `ProfileScreen` (lines 1045–1137)

## Scope

- Port `ProfileScreen` JSX layout verbatim (header block + avatar + email + optional
  PREMIUM badge + list rows). No stats dashboard, no «days streak», no invented rows.
- New file: `lib/screens/profile/profile_screen.dart` (redesign home).
- Route `/shell/profile` → new `ProfileScreen` (old file deleted; empty `screens`
  directory under `features/profile/presentation/` removed).
- Preserved logic: `userProfileNotifierProvider` (AS IS — no edits to domain/data).
- `ApiProfileRepository` untouched.

## Header block (JSX-mapped)

| JSX element | Flutter impl | Notes |
| --- | --- | --- |
| 72×72 circle avatar | `Container(72×72)` circle, `NFColors.lime` bg | First letter of `profile.email` uppercased |
| Avatar letter | `Text(avatarLetter)` | `Nunito 32 / w800`, color `NFColors.limeInk` |
| Email headline | `Text(email)` | `Nunito 24 / w800`, letter-spacing `-0.6`, ellipsis |
| Email subtitle | `NFText.mono('ПОЧТА · $email')` | mono atom (10 / w700, letter-spacing 0.8) |
| `PREMIUM` chip | `_PremiumBadge` | shown only when `subscriptionTier == 'premium'`; `NFColors.lime` bg, `PREMIUM` label in `Nunito 11 / w800` |

## Rows (JSX-mapped)

JSX source uses `ProfileCard` for `bookmarks` / `sources` and references
`goto('plan')` / `goto('preferences')` as sibling entries. Per prompt the Flutter
impl renders all six as uniform tappable list rows (icon + title + chevron):

| # | Row | JSX action | Flutter navigation |
| --- | --- | --- | --- |
| 1 | Сохранённое | `goto('bookmarks')` | `context.go('/shell/bookmarks?kind=bookmark')` |
| 2 | Понравилось | `goto('bookmarks')` liked tab | `context.go('/shell/bookmarks?kind=like')` |
| 3 | Не понравилось | `goto('bookmarks')` disliked tab | `context.go('/shell/bookmarks?kind=dislike')` |
| 4 | Источники | `goto('sources')` | `context.push('/sources')` |
| 5 | План | `goto('plan')` | `context.push('/subscription')` |
| 6 | Настройки | `goto('preferences')` | `context.go('/shell/settings')` |

Icons: `bookmark`, `thumb-up`, `thumb-down`, `radar`, `star`, `gear`, `chevron`
(all exist in `assets/icons/`). Divider: 1px `NFColors.hairline`, inset 38px.

## Grep-proof — no invented sections

`grep -nE "stats|streak|dashboard|days|активность" lib/screens/profile/profile_screen.dart`

```
16:/// likes, dislikes, sources, plan, settings. No stats dashboard, no
```

→ single hit, in the doc comment documenting the **negation** ("No stats dashboard").

`grep -nE "title: '" lib/screens/profile/profile_screen.dart`

```
67:          title: 'Сохранённое',
73:          title: 'Понравилось',
79:          title: 'Не понравилось',
85:          title: 'Источники',
91:          title: 'План',
97:          title: 'Настройки',
```

→ exactly 6 rows, the full set mandated by the spec, nothing else.

## Router

`lib/core/router/app_router.dart`:

- `import '../../features/profile/presentation/screens/profile_screen.dart';`
  → replaced with `import '../../screens/profile/profile_screen.dart';`.
- `GoRoute(path: '/shell/profile', builder: … ProfileScreen())` unchanged.
- Old `lib/features/profile/presentation/screens/profile_screen.dart` deleted;
  the empty parent directory removed. Logout flow lives in Settings — not moved.

## Quality gates

- **Analyze:** `mcp__dart-flutter__analyze_files` on
  `lib/screens/profile/profile_screen.dart` + `lib/core/router/app_router.dart`
  → **0 errors, 0 warnings**.
- **Profile tests:** `flutter test test/features/profile/` →
  **11/11 pass** (ApiProfileRepository contract tests unchanged).
- **Full suite:** same 7 pre-existing failures as on master baseline
  (`test/core/services/token_cache_service_test.dart` timersPending,
  `test/features/auth/presentation/screens/email_verification_code_screen_test.dart`
  EmailVerificationCodeScreen rendering). No new regressions introduced by P20.

## Acceptance checklist

- [x] Renders on mobile / tablet / desktop (uses `ListView` with `Expanded` header).
- [x] Matches JSX `ProfileScreen` layout verbatim (header + 6 rows).
- [x] **No «stats dashboard», no «days streak», no invented sections** — grep-proof above.
- [x] Existing tests (profile) green; pre-existing suite failures unchanged.
- [x] `ApiProfileRepository` / logout flow untouched.
