import 'package:flutter/animation.dart';

/// Neo-futurism (`NF`) motion tokens.
///
/// Four named `(Duration, Curve)` pairs mapped from the mockup CSS:
/// * `fast`  — 180ms · `Curves.easeOut`        (tap press, reaction flash)
/// * `base`  — 240ms · `Curves.easeOutCubic`   (default UI transitions)
/// * `sheet` — 280ms · `Cubic(0.2, 0.9, 0.25, 1)` (bottom-sheet slide-in)
/// * `nav`   — 140ms · `Curves.easeOut`        (nav tab hover / switch)
class NFMotion {
  const NFMotion._();

  static const Duration fastDuration = Duration(milliseconds: 180);
  static const Curve fastCurve = Curves.easeOut;

  static const Duration baseDuration = Duration(milliseconds: 240);
  static const Curve baseCurve = Curves.easeOutCubic;

  static const Duration sheetDuration = Duration(milliseconds: 280);
  static const Curve sheetCurve = Cubic(0.2, 0.9, 0.25, 1);

  static const Duration navDuration = Duration(milliseconds: 140);
  static const Curve navCurve = Curves.easeOut;
}
