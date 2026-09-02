import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Gradient tokens from §5.1–5.4 of design-system.md.
class AppGradients {
  AppGradients._();

  /// §5.2 — Primary CTA gradient (135°, brand-500 → violet-500)
  /// Applied on: login/register/continue buttons, FAB.
  static const LinearGradient ctaPrimary = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF3D5BFF), Color(0xFF7364FF)],
  );

  /// §5.3 — Login/register hero background (160°)
  static const LinearGradient loginHero = LinearGradient(
    begin: Alignment(-0.94, -1.0), // approx 160deg top
    end: Alignment(0.94, 1.0),
    colors: [Color(0xFFEEF1FF), Color(0xFFF7ECFF), Color(0xFFFFE8F1)],
    stops: [0.0, 0.55, 1.0],
  );

  /// §5.4 — Glass gradient (top-to-bottom, white 78% → 55%)
  // rgba(255,255,255, 0.78) = 0xC7; rgba(255,255,255, 0.55) = 0x8C
  static const LinearGradient glass = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xC7FFFFFF), Color(0x8CFFFFFF)],
  );

  /// §5.4 — Glass strong variant
  // rgba(255,255,255, 0.82) = 0xD1; rgba(255,255,255, 0.64) ≈ 0xA3
  static const LinearGradient glassStrong = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xD1FFFFFF), Color(0xA3FFFFFF)],
  );

  // §5.1 Aurora background radial stops (private, used by auroraBackground)
  static const RadialGradient _auroraTop = RadialGradient(
    center: Alignment(-0.6, -1.0), // 20% left, -10% top
    radius: 1.0,
    colors: [Color(0xFFE8EEFF), Colors.transparent],
    stops: [0.0, 0.6],
  );

  static const RadialGradient _auroraBottom = RadialGradient(
    center: Alignment(1.0, 1.0), // 100% right, 100% bottom
    radius: 1.0,
    colors: [Color(0xFFFCE8F0), Colors.transparent],
    stops: [0.0, 0.55],
  );

  /// §5.1 — Aurora background widget.
  /// Wrap screen content with this to apply the aurora gradient background.
  /// Uses RepaintBoundary for performance (see §10.6).
  static Widget auroraBackground({required Widget child}) {
    return RepaintBoundary(
      child: Stack(
        fit: StackFit.expand,
        children: [
          Container(color: AppColors.surfaceBackground),
          Container(decoration: const BoxDecoration(gradient: _auroraTop)),
          Container(decoration: const BoxDecoration(gradient: _auroraBottom)),
          child,
        ],
      ),
    );
  }
}
