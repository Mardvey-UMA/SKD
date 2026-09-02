import 'package:flutter/material.dart';

// Private primitive palette — all raw hex values from §1.1 of design-system.md.
// These are NEVER exported; only AppColors (semantic) is public.
class _Primitive {
  _Primitive._();

  // neutral
  static const Color neutral0 = Color(0xFFFFFFFF);
  static const Color neutral50 = Color(0xFFF5F7FC);
  static const Color neutral100 = Color(0xFFEEF1F9);
  static const Color neutral150 = Color(0xFFE4E8F2);
  static const Color neutral200 = Color(0xFFD0D6E3);
  static const Color neutral300 = Color(0xFFB4BCCC);
  static const Color neutral400 = Color(0xFF8792A6);
  static const Color neutral500 = Color(0xFF667085);
  static const Color neutral600 = Color(0xFF485063);
  static const Color neutral700 = Color(0xFF2E3647);
  static const Color neutral900 = Color(0xFF0E1525);

  // brand
  static const Color brand50 = Color(0xFFEEF1FF);
  static const Color brand100 = Color(0xFFDDE3FF);
  static const Color brand400 = Color(0xFF6E84FF);
  static const Color brand500 = Color(0xFF3D5BFF);
  static const Color brand600 = Color(0xFF2D48E8);
  static const Color brand700 = Color(0xFF1E35C2);
  static const Color violet500 = Color(0xFF7364FF);

  // semantic status
  static const Color success500 = Color(0xFF16B67A);
  static const Color success50 = Color(0xFFE3F7EE);
  static const Color warning500 = Color(0xFFF5A623);
  static const Color warning50 = Color(0xFFFFF2D8);
  static const Color error500 = Color(0xFFE54B6B);
  static const Color error50 = Color(0xFFFDEAEE);
}

/// Accent pastel pair (fg + bg) from §1.2
class Accent {
  const Accent({required this.fg, required this.bg});

  final Color fg;
  final Color bg;
}

/// Public semantic color tokens from §1.3
class AppColors {
  AppColors._();

  // ── Surface ──────────────────────────────────────────────────────────────
  static const Color surfaceBackground = _Primitive.neutral50;
  static const Color surfaceBackgroundSubtle = _Primitive.neutral100;
  static const Color surfaceBase = _Primitive.neutral0;
  static const Color surfaceRaised = _Primitive.neutral0;
  static const Color surfaceSunken = _Primitive.neutral100;

  // Glass (used ONLY with BackdropFilter)
  // rgba(255,255,255, 0.68): alpha = round(0.68*255) = 173 = 0xAD
  static const Color surfaceGlass = Color(0xADFFFFFF);
  // rgba(255,255,255, 0.82): alpha = round(0.82*255) = 209 = 0xD1
  static const Color surfaceGlassStrong = Color(0xD1FFFFFF);
  // rgba(255,255,255, 0.60): alpha = round(0.60*255) = 153 = 0x99
  static const Color surfaceGlassHighlight = Color(0x99FFFFFF);

  // ── Border ────────────────────────────────────────────────────────────────
  // rgba(15,24,40, 0.06): alpha = round(0.06*255) = 15 = 0x0F
  static const Color borderSoft = Color(0x0F0F1828);
  // rgba(15,24,40, 0.10): alpha = round(0.10*255) = 26 = 0x1A
  static const Color borderDefault = Color(0x1A0F1828);
  // rgba(15,24,40, 0.16): alpha = round(0.16*255) = 41 = 0x29
  static const Color borderStrong = Color(0x290F1828);
  static const Color borderFocus = _Primitive.brand500;
  // rgba(61,91,255, 0.16): alpha = round(0.16*255) = 41 = 0x29
  static const Color borderFocusHalo = Color(0x293D5BFF);
  static const Color borderError = _Primitive.error500;
  // rgba(229,75,107, 0.14): alpha = round(0.14*255) = 36 = 0x24
  static const Color borderErrorHalo = Color(0x24E54B6B);

  // ── Text ──────────────────────────────────────────────────────────────────
  static const Color textPrimary = _Primitive.neutral900;
  static const Color textSecondary = _Primitive.neutral600;
  static const Color textTertiary = _Primitive.neutral400;
  static const Color textDisabled = _Primitive.neutral300;
  static const Color textOnPrimary = _Primitive.neutral0;
  static const Color textOnAccent = _Primitive.neutral900;
  static const Color textLink = _Primitive.brand500;
  static const Color textError = _Primitive.error500;
  static const Color textSuccess = _Primitive.success500;

  // ── Interactive ───────────────────────────────────────────────────────────
  static const Color interactivePrimary = _Primitive.brand500;
  static const Color interactivePrimaryHover = _Primitive.brand600;
  static const Color interactivePrimaryPressed = _Primitive.brand700;
  static const Color interactivePrimarySubtle = _Primitive.brand50;
  // rgba(61,91,255, 0.40): alpha = round(0.40*255) = 102 = 0x66
  static const Color interactivePrimaryMuted = Color(0x663D5BFF);

  // ── Status ────────────────────────────────────────────────────────────────
  static const Color success = _Primitive.success500;
  static const Color successBg = _Primitive.success50;
  static const Color warning = _Primitive.warning500;
  static const Color warningBg = _Primitive.warning50;
  static const Color error = _Primitive.error500;
  static const Color errorBg = _Primitive.error50;

  // ── Accent pastel pairs §1.2 ──────────────────────────────────────────────
  static const Accent accentSky = Accent(
    fg: Color(0xFFBCD4FF),
    bg: Color(0xFFE8F0FF),
  );
  static const Accent accentViolet = Accent(
    fg: Color(0xFFC9BEFF),
    bg: Color(0xFFECE7FF),
  );
  static const Accent accentRose = Accent(
    fg: Color(0xFFFFB8D1),
    bg: Color(0xFFFFE5EF),
  );
  static const Accent accentAmber = Accent(
    fg: Color(0xFFFFD88A),
    bg: Color(0xFFFFF2D8),
  );
  static const Accent accentMint = Accent(
    fg: Color(0xFF9FE8CB),
    bg: Color(0xFFE0F7EE),
  );
  static const Accent accentCoral = Accent(
    fg: Color(0xFFFF9B8A),
    bg: Color(0xFFFFE4DE),
  );

  // ── Material3 ColorScheme helpers (used in buildLightTheme) ──────────────
  static const Color m3Primary = _Primitive.brand500;
  static const Color m3OnPrimary = _Primitive.neutral0;
  static const Color m3PrimaryContainer = _Primitive.brand50;
  static const Color m3OnPrimaryContainer = _Primitive.brand700;
  static const Color m3Secondary = _Primitive.violet500;
  static const Color m3OnSecondary = _Primitive.neutral0;
  static const Color m3Surface = _Primitive.neutral0;
  static const Color m3OnSurface = _Primitive.neutral900;
  static const Color m3SurfaceContainerLow = _Primitive.neutral50;
  static const Color m3SurfaceContainer = _Primitive.neutral100;
  static const Color m3SurfaceContainerHigh = _Primitive.neutral150;
  static const Color m3Outline = _Primitive.neutral200;
  static const Color m3Error = _Primitive.error500;
  static const Color m3OnError = _Primitive.neutral0;
  static const Color m3ErrorContainer = _Primitive.error50;
  static const Color m3OnErrorContainer = _Primitive.error500;
}
