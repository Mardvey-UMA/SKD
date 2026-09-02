import 'package:flutter/material.dart';

import 'tokens/app_colors.dart';
import 'tokens/app_elevation.dart';
import 'tokens/app_gradients.dart';
import 'tokens/app_motion.dart';
import 'tokens/app_radii.dart';
import 'tokens/app_spacing.dart';
import 'tokens/app_typography.dart';

export 'tokens/app_colors.dart';
export 'tokens/app_elevation.dart';
export 'tokens/app_gradients.dart';
export 'tokens/app_motion.dart';
export 'tokens/app_radii.dart';
export 'tokens/app_spacing.dart';
export 'tokens/app_typography.dart';

// ── Nested color accessor objects ────────────────────────────────────────────

class TokenSurfaceColors {
  const TokenSurfaceColors();

  Color get background => AppColors.surfaceBackground;
  Color get backgroundSubtle => AppColors.surfaceBackgroundSubtle;
  Color get base => AppColors.surfaceBase;
  Color get raised => AppColors.surfaceRaised;
  Color get sunken => AppColors.surfaceSunken;
  Color get glass => AppColors.surfaceGlass;
  Color get glassStrong => AppColors.surfaceGlassStrong;
  Color get glassHighlight => AppColors.surfaceGlassHighlight;
}

class TokenBorderColors {
  const TokenBorderColors();

  Color get soft => AppColors.borderSoft;
  Color get dfault => AppColors.borderDefault;
  Color get strong => AppColors.borderStrong;
  Color get focus => AppColors.borderFocus;
  Color get focusHalo => AppColors.borderFocusHalo;
  Color get error => AppColors.borderError;
  Color get errorHalo => AppColors.borderErrorHalo;
}

class TokenTextColors {
  const TokenTextColors();

  Color get primary => AppColors.textPrimary;
  Color get secondary => AppColors.textSecondary;
  Color get tertiary => AppColors.textTertiary;
  Color get disabled => AppColors.textDisabled;
  Color get onPrimary => AppColors.textOnPrimary;
  Color get onAccent => AppColors.textOnAccent;
  Color get link => AppColors.textLink;
  Color get error => AppColors.textError;
  Color get success => AppColors.textSuccess;
}

class TokenInteractiveColors {
  const TokenInteractiveColors();

  Color get primary => AppColors.interactivePrimary;
  Color get primaryHover => AppColors.interactivePrimaryHover;
  Color get primaryPressed => AppColors.interactivePrimaryPressed;
  Color get primarySubtle => AppColors.interactivePrimarySubtle;
  Color get primaryMuted => AppColors.interactivePrimaryMuted;
}

class TokenStatusColors {
  const TokenStatusColors();

  Color get success => AppColors.success;
  Color get successBg => AppColors.successBg;
  Color get warning => AppColors.warning;
  Color get warningBg => AppColors.warningBg;
  Color get error => AppColors.error;
  Color get errorBg => AppColors.errorBg;
}

/// Top-level color accessor. Access via `tokens.colors.interactive.primary`.
class TokenColors {
  const TokenColors();

  TokenSurfaceColors get surface => const TokenSurfaceColors();
  TokenBorderColors get border => const TokenBorderColors();
  TokenTextColors get text => const TokenTextColors();
  TokenInteractiveColors get interactive => const TokenInteractiveColors();
  TokenStatusColors get status => const TokenStatusColors();

  Accent get accentSky => AppColors.accentSky;
  Accent get accentViolet => AppColors.accentViolet;
  Accent get accentRose => AppColors.accentRose;
  Accent get accentAmber => AppColors.accentAmber;
  Accent get accentMint => AppColors.accentMint;
  Accent get accentCoral => AppColors.accentCoral;
}

// ── Typography accessor ───────────────────────────────────────────────────────

class TokenTypography {
  const TokenTypography();

  TextStyle get displayXl => AppTypography.displayXl;
  TextStyle get displayL => AppTypography.displayL;
  TextStyle get headingL => AppTypography.headingL;
  TextStyle get headingM => AppTypography.headingM;
  TextStyle get headingS => AppTypography.headingS;
  TextStyle get bodyL => AppTypography.bodyL;
  TextStyle get bodyM => AppTypography.bodyM;
  TextStyle get bodyS => AppTypography.bodyS;
  TextStyle get caption => AppTypography.caption;
  TextStyle get button => AppTypography.button;
  TextStyle get overline => AppTypography.overline;
  TextStyle get numeric => AppTypography.numeric;
}

// ── Spacing accessor ──────────────────────────────────────────────────────────

class TokenSpacing {
  const TokenSpacing();

  double get xs2 => AppSpacing.xs2;
  double get xs => AppSpacing.xs;
  double get sm => AppSpacing.sm;
  double get md => AppSpacing.md;
  double get lg => AppSpacing.lg;
  double get xl => AppSpacing.xl;
  double get xl2 => AppSpacing.xl2;
  double get xl3 => AppSpacing.xl3;
  double get xl4 => AppSpacing.xl4;
}

// ── Radii accessor ────────────────────────────────────────────────────────────

class TokenRadii {
  const TokenRadii();

  Radius get xs => AppRadii.xs;
  Radius get sm => AppRadii.sm;
  Radius get md => AppRadii.md;
  Radius get lg => AppRadii.lg;
  Radius get xl => AppRadii.xl;
  Radius get xl2 => AppRadii.xl2;
  Radius get full => AppRadii.full;

  BorderRadius get bxs => AppRadii.bxs;
  BorderRadius get bsm => AppRadii.bsm;
  BorderRadius get bmd => AppRadii.bmd;
  BorderRadius get blg => AppRadii.blg;
  BorderRadius get bxl => AppRadii.bxl;
  BorderRadius get bxl2 => AppRadii.bxl2;
  BorderRadius get bfull => AppRadii.bfull;
}

// ── Elevation accessor ────────────────────────────────────────────────────────

class TokenElevation {
  const TokenElevation();

  List<BoxShadow> get elev0 => AppElevation.elev0;
  List<BoxShadow> get elev1 => AppElevation.elev1;
  List<BoxShadow> get elev2 => AppElevation.elev2;
  List<BoxShadow> get elev3 => AppElevation.elev3;
  List<BoxShadow> get elev4 => AppElevation.elev4;
  List<BoxShadow> get glassNav => AppElevation.glassNav;
  List<BoxShadow> get glassModal => AppElevation.glassModal;
}

// ── Gradients accessor ────────────────────────────────────────────────────────

class TokenGradients {
  const TokenGradients();

  LinearGradient get ctaPrimary => AppGradients.ctaPrimary;
  LinearGradient get loginHero => AppGradients.loginHero;
  LinearGradient get glass => AppGradients.glass;
  LinearGradient get glassStrong => AppGradients.glassStrong;

  Widget auroraBackground({required Widget child}) =>
      AppGradients.auroraBackground(child: child);
}

// ── Motion accessor ───────────────────────────────────────────────────────────

class TokenMotion {
  const TokenMotion();

  Duration get instant => AppMotion.instant;
  Duration get micro => AppMotion.micro;
  Duration get fast => AppMotion.fast;
  Duration get base => AppMotion.base;
  Duration get emphasized => AppMotion.emphasized;
  Duration get ambient => AppMotion.ambient;

  Curve get standard => AppMotion.standard;
  Curve get expressive => AppMotion.expressive;
  Curve get snappy => AppMotion.snappy;
  Curve get spring => AppMotion.spring;
}

// ── AppTokens ThemeExtension ──────────────────────────────────────────────────

/// ThemeExtension that provides access to all design tokens.
/// Access via `Theme.of(context).extension<AppTokens>()!` or
/// the `context.tokens` shorthand from ThemeX extension.
///
/// Usage:
///   tokens.colors.interactive.primary  == Color(0xFF3D5BFF)
///   tokens.colors.surface.background   == Color(0xFFF5F7FC)
///   tokens.typography.headingL.fontFamily == 'Manrope'
class AppTokens extends ThemeExtension<AppTokens> {
  const AppTokens();

  /// Singleton light instance for use in ThemeData.extensions.
  static const AppTokens light = AppTokens();

  // ── Token accessors ────────────────────────────────────────────────────────

  TokenColors get colors => const TokenColors();
  TokenTypography get typography => const TokenTypography();
  TokenSpacing get spacing => const TokenSpacing();
  TokenRadii get radii => const TokenRadii();
  TokenElevation get elevation => const TokenElevation();
  TokenGradients get gradients => const TokenGradients();
  TokenMotion get motion => const TokenMotion();

  // ── ThemeExtension API ─────────────────────────────────────────────────────

  @override
  AppTokens copyWith() => const AppTokens();

  @override
  AppTokens lerp(ThemeExtension<AppTokens>? other, double t) {
    if (other is! AppTokens) return this;
    // All tokens are static constants; no interpolation needed.
    return t < 0.5 ? this : other;
  }
}
