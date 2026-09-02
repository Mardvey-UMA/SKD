import 'package:flutter/material.dart';

import 'app_tokens.dart';
import 'colors.dart';
import 'radii.dart';
import 'typography.dart';

/// Composes the Radar (`NF`) light [ThemeData].
///
/// Built from explicit tokens — NO `ColorScheme.fromSeed`, no Material 3
/// harmonization. Each color, radius and text style maps directly to
/// `design/reference/radar-redesign-prompts.md` § «Design tokens».
///
/// There is intentionally no dark theme: the mockup ships light-only.
class RadarTheme {
  const RadarTheme._();

  static ThemeData light() {
    const ColorScheme colorScheme = ColorScheme(
      brightness: Brightness.light,
      primary: NFColors.ink,
      onPrimary: NFColors.surface,
      secondary: NFColors.accent,
      onSecondary: NFColors.accentInk,
      tertiary: NFColors.lime,
      onTertiary: NFColors.limeInk,
      error: NFColors.warn,
      onError: NFColors.surface,
      surface: NFColors.surface,
      onSurface: NFColors.ink,
      surfaceContainerLowest: NFColors.surface,
      surfaceContainerLow: NFColors.bg,
      surfaceContainer: NFColors.surface,
      surfaceContainerHigh: NFColors.surface2,
      surfaceContainerHighest: NFColors.chipBg,
      outline: NFColors.hairline,
      outlineVariant: NFColors.hairline,
      shadow: Color(0xFF000000),
      scrim: Color(0xFF000000),
      inverseSurface: NFColors.ink,
      onInverseSurface: NFColors.surface,
      inversePrimary: NFColors.lime,
    );

    final TextTheme textTheme = NFTypography.toTextTheme();

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      textTheme: textTheme,
      extensions: const <ThemeExtension<dynamic>>[AppTokens.light],
      scaffoldBackgroundColor: NFColors.bg,
      canvasColor: NFColors.bg,
      dividerColor: NFColors.hairline,
      splashFactory: InkRipple.splashFactory,

      appBarTheme: AppBarTheme(
        backgroundColor: NFColors.bg,
        foregroundColor: NFColors.ink,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: NFTypography.h1.copyWith(color: NFColors.ink),
      ),

      cardTheme: const CardThemeData(
        color: NFColors.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: NFRadii.brLg),
      ),

      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: NFColors.ink,
          foregroundColor: NFColors.surface,
          shape: const RoundedRectangleBorder(borderRadius: NFRadii.br),
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          textStyle: NFTypography.meta,
        ),
      ),

      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: NFColors.ink,
          foregroundColor: NFColors.surface,
          shape: const RoundedRectangleBorder(borderRadius: NFRadii.br),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          textStyle: NFTypography.meta,
        ),
      ),

      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: NFColors.ink,
          side: const BorderSide(color: NFColors.hairline),
          shape: const RoundedRectangleBorder(borderRadius: NFRadii.br),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          textStyle: NFTypography.meta,
        ),
      ),

      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: NFColors.ink,
          shape: const RoundedRectangleBorder(borderRadius: NFRadii.br),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          textStyle: NFTypography.meta,
        ),
      ),

      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: NFColors.surface,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        hintStyle: NFTypography.body.copyWith(color: NFColors.mute),
        labelStyle: NFTypography.meta.copyWith(color: NFColors.mute),
        border: const OutlineInputBorder(
          borderRadius: NFRadii.br,
          borderSide: BorderSide(color: NFColors.hairline),
        ),
        enabledBorder: const OutlineInputBorder(
          borderRadius: NFRadii.br,
          borderSide: BorderSide(color: NFColors.hairline),
        ),
        focusedBorder: const OutlineInputBorder(
          borderRadius: NFRadii.br,
          borderSide: BorderSide(color: NFColors.ink, width: 1.5),
        ),
        errorBorder: const OutlineInputBorder(
          borderRadius: NFRadii.br,
          borderSide: BorderSide(color: NFColors.warn),
        ),
        focusedErrorBorder: const OutlineInputBorder(
          borderRadius: NFRadii.br,
          borderSide: BorderSide(color: NFColors.warn, width: 1.5),
        ),
      ),

      dividerTheme: const DividerThemeData(
        color: NFColors.hairline,
        space: 0,
        thickness: 1,
      ),

      chipTheme: ChipThemeData(
        backgroundColor: NFColors.chipBg,
        side: BorderSide.none,
        shape: const StadiumBorder(),
        labelStyle: NFTypography.meta.copyWith(color: NFColors.ink),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      ),

      iconTheme: const IconThemeData(color: NFColors.ink2, size: 22),

      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: NFColors.surface,
        surfaceTintColor: NFColors.surface,
        modalBackgroundColor: NFColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
      ),

      snackBarTheme: SnackBarThemeData(
        backgroundColor: NFColors.ink,
        contentTextStyle: NFTypography.meta.copyWith(color: NFColors.surface),
        shape: const RoundedRectangleBorder(borderRadius: NFRadii.br),
        behavior: SnackBarBehavior.floating,
      ),

      dialogTheme: const DialogThemeData(
        backgroundColor: NFColors.surface,
        surfaceTintColor: NFColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: NFRadii.brLg),
      ),
    );
  }
}
