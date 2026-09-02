# Prompt 23 — Page transitions + screen fade

**Phase:** 5 · Motion · **Depends on:** 13 + все Phase-4 screens (16–22)
**Agent entry:** `/dev page transitions motion`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 23

## Reference files (read-only)

- `design/reference/mockup/radar-web.html` — `@keyframes fade`, `animation: fade 260ms ease`

## Target files

- `lib/ui/motion/page_transitions.dart`
- `lib/responsive/screen_fade.dart`
- `lib/core/router/app_router.dart` (edit — подключить custom `PageRouteBuilder`)

## Task

1. Custom `PageRouteBuilder` — fade 260ms + rise 4px. Маппинг 1:1 с CSS `@keyframes fade`.
2. `ScreenFade` обертка над `AnimatedSwitcher(duration: 260ms)` с `FadeTransition + SlideTransition(offset: 0 → Offset(0, 0.004))`.
3. Hero-transition для image на переходе `Feed → Detail`:
   - Tag: `'feed-hero-{itemId}'`
   - Curve: `Curves.easeOutCubic`
   - Duration: 320ms
   - `FlightShuttleBuilder` с `ClipRRect` — stripe pattern не должен искажаться.

## Acceptance criteria

- [ ] Быстрое переключение табов не дропает кадры (проверить Chrome DevTools Performance → минимум 55fps).
- [ ] Hero-анимация: stripe-pattern остаётся выровнен / не деформируется.
- [ ] Existing integration-тесты проходят.

## Do NOT

- Не превышать 320ms на hero — дольше ощущается лагом.
- Не вводить пакеты (`animations` pub: можно использовать ТОЛЬКО если уже был в pubspec).
