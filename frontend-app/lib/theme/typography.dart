import 'package:flutter/material.dart';

import 'colors.dart';

/// Neo-futurism (`NF`) typography tokens.
///
/// Single font family: **Nunito** — served from the local bundle
/// (`assets/fonts/Nunito-Variable.ttf`) so there is no runtime network
/// fetch and therefore no FOUT on first paint. The mockup reuses Nunito
/// for both SANS and MONO; "mono" look is achieved with uppercase +
/// letter-spacing 0.8 (uppercase happens at the widget layer — see
/// `Do NOT` rule in Prompt 01).
///
/// Weights referenced across the app: 400, 500, 600, 700, 800, 900.
///
/// Display responsive sizes (38/42/46) resolve in the widget layer —
/// this file exposes the baseline `display` style at 42 / −1.4.
class NFTypography {
  const NFTypography._();

  static const String _family = 'Nunito';

  static TextStyle _nunito({
    required double fontSize,
    required FontWeight fontWeight,
    double letterSpacing = 0,
    double? height,
    Color color = NFColors.ink,
  }) {
    return TextStyle(
      fontFamily: _family,
      fontSize: fontSize,
      fontWeight: fontWeight,
      letterSpacing: letterSpacing,
      height: height,
      color: color,
    );
  }

  /// Hero / display (base: 42 · w800 · −1.4).
  /// Responsive 38/42/46 with letter-spacing −1.2/−1.4/−1.6 is resolved at
  /// the widget layer (see Prompt 04 `NFText.display`).
  static final TextStyle display = _nunito(
    fontSize: 42,
    fontWeight: FontWeight.w800,
    letterSpacing: -1.4,
    height: 1.05,
  );

  /// H1 — 22 · w700 · −0.6.
  static final TextStyle h1 = _nunito(
    fontSize: 22,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.6,
    height: 1.14,
  );

  /// H2 — 21 · w600 · −0.5.
  static final TextStyle h2 = _nunito(
    fontSize: 21,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.5,
    height: 1.15,
  );

  /// Body — 14.5 · w400 · 1.5 line-height.
  static final TextStyle body = _nunito(
    fontSize: 14.5,
    fontWeight: FontWeight.w400,
    height: 1.5,
    color: NFColors.ink2,
  );

  /// Meta — 13 · w600.
  static final TextStyle meta = _nunito(
    fontSize: 13,
    fontWeight: FontWeight.w600,
    color: NFColors.mute,
  );

  /// Mono — 10 · w700 · tracking 0.8. Uppercase is applied at widget level
  /// (see `Web → Flutter mapping` in Prompt 01 + `NFText.mono` in Prompt 04).
  static final TextStyle mono = _nunito(
    fontSize: 10,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.8,
    color: NFColors.mute,
  ).copyWith(fontFeatures: const <FontFeature>[FontFeature.tabularFigures()]);

  /// Text-theme adapter used by `RadarTheme.light()` to satisfy Material
  /// widgets that resolve text via `Theme.of(context).textTheme`. Unused
  /// tokens fall back to body.
  static TextTheme toTextTheme() {
    return TextTheme(
      displayLarge: display,
      displayMedium: display,
      displaySmall: display,
      headlineLarge: h1,
      headlineMedium: h1,
      headlineSmall: h2,
      titleLarge: h2,
      titleMedium: h2,
      titleSmall: meta,
      bodyLarge: body,
      bodyMedium: body,
      bodySmall: meta,
      labelLarge: meta,
      labelMedium: meta,
      labelSmall: mono,
    );
  }
}
