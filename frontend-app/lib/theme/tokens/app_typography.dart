import 'package:flutter/material.dart';

/// Typography scale from §2.2 of design-system.md.
/// Manrope for display-xl, display-l, heading-l; Inter for everything else.
/// Line height set via height = lineHeightPx / fontSizePx.
/// Letter spacing converted from em: letterSpacingEm * fontSize.
class AppTypography {
  AppTypography._();

  static const String _display = 'Manrope';
  static const String _text = 'Inter';

  // §2.2 scale

  /// 32px / 40 lh / 700 / -0.015em — Hero headings (onboarding)
  static const TextStyle displayXl = TextStyle(
    fontFamily: _display,
    fontSize: 32,
    height: 40 / 32, // 1.25
    fontWeight: FontWeight.w700,
    letterSpacing: -0.015 * 32, // -0.48
  );

  /// 28px / 36 lh / 700 / -0.013em — Large screen headings
  static const TextStyle displayL = TextStyle(
    fontFamily: _display,
    fontSize: 28,
    height: 36 / 28,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.013 * 28, // -0.364
  );

  /// 22px / 28 lh / 600 / -0.008em — Page titles (Settings, Collections)
  static const TextStyle headingL = TextStyle(
    fontFamily: _display,
    fontSize: 22,
    height: 28 / 22,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.008 * 22, // -0.176
  );

  /// 18px / 24 lh / 600 / -0.004em — Card headings, avatar email
  static const TextStyle headingM = TextStyle(
    fontFamily: _text,
    fontSize: 18,
    height: 24 / 18,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.004 * 18, // -0.072
  );

  /// 16px / 22 lh / 600 / 0 — Menu item names
  static const TextStyle headingS = TextStyle(
    fontFamily: _text,
    fontSize: 16,
    height: 22 / 16,
    fontWeight: FontWeight.w600,
    letterSpacing: 0,
  );

  /// 16px / 24 lh / 400 / 0 — Body text
  static const TextStyle bodyL = TextStyle(
    fontFamily: _text,
    fontSize: 16,
    height: 24 / 16,
    fontWeight: FontWeight.w400,
    letterSpacing: 0,
  );

  /// 14px / 20 lh / 400 / 0 — Card descriptions, secondary text
  static const TextStyle bodyM = TextStyle(
    fontFamily: _text,
    fontSize: 14,
    height: 20 / 14,
    fontWeight: FontWeight.w400,
    letterSpacing: 0,
  );

  /// 13px / 18 lh / 400 / +0.002em — Meta info (author · date)
  static const TextStyle bodyS = TextStyle(
    fontFamily: _text,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w400,
    letterSpacing: 0.002 * 13, // 0.026
  );

  /// 12px / 16 lh / 500 / +0.025em — Section labels (UPPERCASE), badges
  static const TextStyle caption = TextStyle(
    fontFamily: _text,
    fontSize: 12,
    height: 16 / 12,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.025 * 12, // 0.30
  );

  /// 15px / 20 lh / 600 / +0.004em — Button text
  static const TextStyle button = TextStyle(
    fontFamily: _text,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.004 * 15, // 0.06
  );

  /// 11px / 14 lh / 600 / +0.06em — UPPERCASE section labels
  static const TextStyle overline = TextStyle(
    fontFamily: _text,
    fontSize: 11,
    height: 14 / 11,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.06 * 11, // 0.66
  );

  /// 18px / 24 lh / 600 / 0 — Timers, counters (tabular figures)
  static const TextStyle numeric = TextStyle(
    fontFamily: _text,
    fontSize: 18,
    height: 24 / 18,
    fontWeight: FontWeight.w600,
    letterSpacing: 0,
    fontFeatures: [FontFeature.tabularFigures()],
  );

  /// Maps design tokens to Material 3 TextTheme roles.
  static TextTheme toTextTheme() {
    return TextTheme(
      displayLarge: displayXl,
      displayMedium: displayL,
      displaySmall: headingL,
      headlineLarge: headingL,
      headlineMedium: headingM,
      headlineSmall: headingS,
      titleLarge: headingM,
      titleMedium: headingS,
      titleSmall: bodyL.copyWith(fontWeight: FontWeight.w600),
      bodyLarge: bodyL,
      bodyMedium: bodyM,
      bodySmall: bodyS,
      labelLarge: button,
      labelMedium: caption,
      labelSmall: overline,
    );
  }
}
