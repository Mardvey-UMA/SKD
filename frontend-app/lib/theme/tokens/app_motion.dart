import 'package:flutter/material.dart';

/// Motion tokens from §7.1–7.2 of design-system.md.
class AppMotion {
  AppMotion._();

  // §7.2 — Durations

  /// 80ms — Hover, focus ring
  static const Duration instant = Duration(milliseconds: 80);

  /// 120ms — Ripple, press-scale
  static const Duration micro = Duration(milliseconds: 120);

  /// 220ms — State switches, chip toggles
  static const Duration fast = Duration(milliseconds: 220);

  /// 320ms — Card appearance, sheet open
  static const Duration base = Duration(milliseconds: 320);

  /// 480ms — Page transitions, onboarding slides
  static const Duration emphasized = Duration(milliseconds: 480);

  /// 800ms — Background CTA pulse
  static const Duration ambient = Duration(milliseconds: 800);

  // §7.1 — Curves (exact Cubic coefficients from design-system.md)

  /// Most transitions — Cubic(0.4, 0.0, 0.2, 1.0)
  static const Curve standard = Cubic(0.4, 0.0, 0.2, 1.0);

  /// Hero element appearances — Cubic(0.22, 1.0, 0.36, 1.0)
  static const Curve expressive = Cubic(0.22, 1.0, 0.36, 1.0);

  /// Fast tap responses — Cubic(0.32, 0.72, 0.0, 1.0)
  static const Curve snappy = Cubic(0.32, 0.72, 0.0, 1.0);

  /// Bottom-sheet with overshoot — Cubic(0.5, 1.6, 0.4, 1.0)
  static const Curve spring = Cubic(0.5, 1.6, 0.4, 1.0);
}
