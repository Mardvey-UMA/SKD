# P04 · Text atoms (NFText)

## Files

Created:
- `lib/ui/atoms/nf_text.dart` — `NFText` widget with named constructors
  `display / h1 / h2 / body / meta / mono`.
- `test/ui/atoms/nf_text_test.dart` — widget tests for mono uppercase +
  styling, color override, and display breakpoint resolution (explicit +
  `context.breakpoint` fallback).

Edited: none.

## API summary

- `NFText.display(String, {Breakpoint? breakpoint, ...})` — 38/42/46 ·
  letter −1.2/−1.4/−1.6. If `breakpoint` is omitted, reads
  `context.breakpoint` (`lib/responsive/context_ext.dart`).
- `NFText.h1|h2|body|meta(String, {...})` — thin wrappers over
  `NFTypography.*` tokens from P01.
- `NFText.mono(String, {...})` — **uppercases value internally**
  (`data.toUpperCase()`), applies `fontSize: 10`, `FontWeight.w700`,
  `letterSpacing: 0.8`, default `color: NFColors.mute` (overridable via
  `color:` arg).
- All constructors accept `textAlign`, `maxLines`, `overflow`, `color`.
- No inline `TextStyle`, no `GoogleFonts`, no new deps, no changes to
  `lib/theme/**` or `lib/features/**`.

## Verification

- `flutter analyze lib/ui/atoms/nf_text.dart test/ui/atoms/nf_text_test.dart`
  → **No issues found** (clean).
- `flutter test test/ui/atoms/` → **4 / 4 passed**:
  - `NFText.mono uppercases the value and applies mono styling`
  - `NFText.mono color override wins over default mute`
  - `NFText.display explicit breakpoint resolves size/letter-spacing`
  - `NFText.display falls back to context.breakpoint when arg omitted`

## Commit

`30e923067a90f21944521af4fa5a463533c4b297` — `feat(ui): NFText atom with
mono/display/h1/h2/body/meta (redesign P04)`.
