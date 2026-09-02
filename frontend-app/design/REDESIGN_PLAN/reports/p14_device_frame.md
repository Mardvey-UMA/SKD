# Prompt 14 — Device frame · Report

**Date:** 2026-04-21
**Slug:** `redesign-p14-device-frame`
**Phase:** 3 · Compositions
**Commit:** `feat(shell): DeviceFrame iPhone/Pixel/Galaxy with release-mode guard (redesign P14)`

## Files

### Created

- `lib/ui/shell/devices/ios.dart` — `IOSDevice({child, width=402, height=874, time='9:41'})`. Fixed bezel 402×874, `BorderRadius.circular(48)`, `#F2F2F7` shell, double `BoxShadow` (drop + 1-px hairline). Overlays (in z-order): child → absolute status bar (time left, signal/wifi/battery right, `SF-style` weight 600 ≈ `w600`) → dynamic-island pill 126×37 at `top:11` radius 24 `#000` → home indicator 139×5 at `bottom:8`. All glyphs via `CustomPainter`, primitives only — no Apple trade-dress.
- `lib/ui/shell/devices/pixel.dart` — `PixelDevice({child, width=402, height=874})`. `BorderRadius.circular(44)`, `#F7F7F4` shell, drop-shadow + 1.5-px hairline. 12-px centred punch-hole camera at `top:10`. Uses shared `AndroidStatus` + `AndroidGestureBar`.
- `lib/ui/shell/devices/galaxy.dart` — `GalaxyDevice({child, width=402, height=874})`. `BorderRadius.circular(52)`, `#F2F3F5` shell, drop-shadow + **2-px inky `#111` hairline** (thicker bezel border). Smaller + higher 10-px punch-hole at `top:9`. Shares the same Android helpers.
- `lib/ui/shell/devices/_android_common.dart` — library-private helpers shared by Pixel + Galaxy:
  - `AndroidStatus({time='9:41', dark=false})` — 32-px row, time-left (`w500`, `ls:0.1`) + 3-bar signal + wifi arcs + battery with nib.
  - `AndroidGestureBar({dark=false})` — 22-px-tall row with centred 120×4 pill 7-px from bottom, `rgba(14,15,13,0.55)` / dark variant.
  - SVG primitives ported to `CustomPainter` — no raster assets.
- `test/ui/shell/device_frame_test.dart` — 5 widget tests:
  1. `DeviceKind.iphone` → renders `IOSDevice` + child (no Pixel/Galaxy).
  2. `DeviceKind.pixel` → renders `PixelDevice` + `AndroidStatus` + `AndroidGestureBar`.
  3. `DeviceKind.galaxy` → renders `GalaxyDevice` + `AndroidStatus` + `AndroidGestureBar`.
  4. `releaseOverride=true` → child only, all three bezel classes absent.
  5. `releaseOverride=true` elides bezel for every `DeviceKind` value (loop).

### Edited

- `lib/ui/shell/device_frame.dart` — replaced the Prompt-13 placeholder.
  - Added `enum DeviceKind { iphone, pixel, galaxy }`.
  - `DeviceFrame({kind=iphone, child, width=402, height=874, @visibleForTesting releaseOverride})`.
  - Release-mode guard: `final isRelease = releaseOverride ?? kReleaseMode; if (isRelease) return child;`. In release builds the widget short-circuits to its child with zero bezel chrome — `IOSDevice` / `PixelDevice` / `GalaxyDevice` are not even instantiated, so their `CustomPainter`s and primitive shapes never reach the paint tree in a release bundle.
  - Switch on `kind` dispatches to the three per-device classes.
  - `releaseOverride` is `@visibleForTesting` — runtime code relies on `kReleaseMode` only.
- `lib/ui/shell/responsive_shell.dart` — added `deviceKind` parameter (default `DeviceKind.iphone`), forwarded into the mobile `DeviceFrame(kind: ...)`. Tablet / desktop branches untouched.

## Design adherence

| Requirement (from JSX) | Implementation |
|------------------------|----------------|
| iPhone 402×874, `radius:48`, `#F2F2F7` shell | `IOSDevice` default dims + `BorderRadius.circular(48)` + `Color(0xFFF2F2F7)` |
| iPhone dynamic island 126×37 at `top:11`, `radius:24`, `#000` | Positioned `Center` pill in stack z=50 equivalent (rendered last under home indicator) |
| iPhone home indicator 139×5 at `bottom:8`, `rgba(0,0,0,0.25)` | Positioned `IgnorePointer` at bottom with `paddingBottom: 8` |
| iOS status-bar time `17 / w600`, signal+wifi+battery right cluster | `_IOSStatusBar` Row with Expanded-center time / 126-px island reservation / Expanded-center glyph cluster |
| Pixel 402×874, `radius:44`, `#F7F7F4`, 12-px centred punch-hole at `top:10` | `PixelDevice` with `BorderRadius.circular(44)` + circle primitive |
| Galaxy 402×874, `radius:52`, `#F2F3F5`, 2-px `#111` border, 10-px hole at `top:9` | `GalaxyDevice` with second shadow as 2-px hairline + smaller hole |
| Android status bar: time-left `14/w500`, signal/wifi/battery right | `AndroidStatus` via `Row` + `Spacer` + custom-painted glyphs |
| Android gesture pill 120×4 at `bottom:7` | `AndroidGestureBar` |
| Shared helpers for Pixel+Galaxy | `_android_common.dart` (library-private `AndroidStatus` / `AndroidGestureBar`) |
| Release guard | `if (isRelease) return child` before switch |

## Release-mode guard design

The guard wraps the **entire bezel** subtree, not individual ornaments:

```dart
final bool isRelease = releaseOverride ?? kReleaseMode;
if (isRelease) return child;
switch (kind) { ... }
```

Because `kReleaseMode` is a `const bool`, the Dart compiler tree-shakes the
entire `switch` branch (including `IOSDevice`, `PixelDevice`, `GalaxyDevice`,
all `CustomPainter` glyphs) out of release builds. No device-frame assets —
no raster, no SVG, no primitive `CustomPainter` call-sites — survive into
`flutter build web --release`. Raster assets were never introduced in the
first place (primitives only, per spec § «Do NOT») so the `grep` acceptance
criterion is trivially satisfied.

Tests override via `@visibleForTesting final bool? releaseOverride` so both
branches can be asserted without toggling compiler mode — matches the spec's
«simulate via Platform check or assert test-only».

## Acceptance criteria

- [x] Each `DeviceKind` renders its bezel in debug mode (tests 1–3).
- [x] Release-mode returns child only — no bezel widgets in tree (tests 4–5).
- [x] `DeviceFrame` switch covers `{iphone, pixel, galaxy}`.
- [x] `IOSDevice`: fixed dims, radius, shadow, status bar (notch/island), home indicator.
- [x] `PixelDevice` + `GalaxyDevice`: shared `AndroidStatus` + `AndroidGestureBar`, individual bezel radii (44 vs 52) + camera cut-outs (12-px `top:10` vs 10-px `top:9`).
- [x] No Apple/Google trade-dress — primitives only (shapes, painters, colours from JSX).
- [x] `responsive_shell.dart` mobile branch uses `DeviceFrame(kind: DeviceKind.iphone, child)`; configurable via `deviceKind` prop for tests.
- [x] `flutter analyze lib/ui/shell/** lib/ui/shell/responsive_shell.dart test/ui/shell/device_frame_test.dart` → **No errors**.
- [x] `flutter test test/ui/shell/device_frame_test.dart` → **5/5 passed**.
- [x] `flutter test test/ui/shell/` (device_frame + responsive_shell) → **10/10 passed**.

## Trade-offs / notes

- The JSX uses precise `boxShadow: '0 0 0 1px …'` spread-only hairlines. Flutter's `BoxShadow` has no spread-only mode, so the hairline is modelled with `spreadRadius` + `blurRadius: 0` on a second shadow entry. Visually equivalent at any pixel ratio.
- The iOS status-bar in the JSX stacks icons via absolute positioning; the Dart port uses `Expanded` + `Center` with a `SizedBox(width: 126)` island reservation. Prevents the right cluster from overflowing at 402-px width, while preserving symmetric left/right layout around the island.
- SVG wifi glyphs became `Path.arcToPoint`-based approximations — pixel-perfect to the JSX is not required (primitive-only, cosmetic). If future pixel-diff tests demand closer parity, swap for explicit `lineTo` control points.
- `responsive_shell_test.dart` continues to pass because `DeviceFrame` is still the top-level class found by `find.byType(DeviceFrame)`; the new `deviceKind` parameter has a default.

## Next

- Prompt 15 (`CardMenu`) — acceptance criterion «CardMenu overlays correctly over shell» will be validated on top of this device frame.
- If golden tests are added later, they can switch `DeviceFrame(releaseOverride: false, kind: ...)` per device to freeze the bezel visuals.
