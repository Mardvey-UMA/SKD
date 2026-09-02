# Prompt 14 — Device frame: iPhone / Pixel / Galaxy

**Phase:** 3 · Compositions · **Depends on:** 01
**Agent entry:** `/dev device frame`
**Source of truth:** `design/reference/radar-redesign-prompts.md` § Prompt 14

## Reference files (read-only)

- `design/reference/mockup/device-frame.jsx`
- `design/reference/mockup/ios-frame.jsx`
- `design/reference/mockup/android-frame.jsx`

## Target files (create / edit)

- `lib/ui/shell/device_frame.dart` (edit из Prompt 13; раскрыть placeholder)
- `lib/ui/shell/devices/ios.dart`
- `lib/ui/shell/devices/pixel.dart`
- `lib/ui/shell/devices/galaxy.dart`

## Task

1. На каждое устройство:
   - Фиксированные bezel-dim-ы и `borderRadius` — из JSX.
   - Shadow.
   - Status bar в верху.
   - Home / gesture-bar внизу.
2. `IOSDevice`: статус-бар с notch/dynamic island + home-indicator.
3. `PixelDevice` / `GalaxyDevice`: общие `AndroidStatus` + `AndroidGestureBar` хелперы, индивидуальные bezel-radii / камеры.
4. `DeviceFrame(kind: DeviceKind.iphone|.pixel|.galaxy, child)` — switch.
5. **Release-mode guard**: оборачивай рендер в `if (kReleaseMode) return child; else return {bezel + child}`. Ассеты frame не должны попадать в release bundle.

## Acceptance criteria

- [ ] Golden на каждое устройство.
- [ ] `flutter build web --release` → в `build/web` **нет** device-frame assets (проверить grep).

## Do NOT

- Не вставлять реальные trade-dress ассеты Apple/Google.
- Не шить device-frame в release.
