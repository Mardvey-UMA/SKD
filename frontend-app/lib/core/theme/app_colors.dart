import 'package:flutter/material.dart';

/// Application color constants for dark theme matching WelcomeScreen.png mockup.
class AppColors {
  AppColors._();

  // Background colors
  static const Color background = Color(0xFFF7F7F9);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceLight = Color(0xFFF3F4F6);

  // Auth Gradient Background
  static const Color authBackgroundGradientTop = Color(0xFFEBF0FE);
  static const Color authBackgroundGradientBottom = Color(0xFFFAEEEE);
  static const Color authBackgroundGradientAccent = Color(0xFFFFCCEA);

  // Accent colors
  static const Color primary = Color(0xFF2B5CFC); // Vibrant Blue from Mockup
  static const Color primaryLight = Color(0xFF6B8AFF);
  static const Color primaryDark = Color(0xFF143BD6);
  static const Color gradientStart = Color(0xFF2B5CFC);
  static const Color gradientEnd = Color(0xFF143BD6);

  // Text colors
  static const Color textPrimary = Color(0xFF1F2937); // Dark text
  static const Color textSecondary = Color(0xFF6B7280); // Gray text
  static const Color textHint = Color(0xFF9CA3AF);

  // Input field colors
  static const Color inputFill = Color(0xFFFFFFFF);
  static const Color inputBorder = Color(0xFFE5E7EB);
  // §9.3 token-aligned border states
  static const Color inputBorderFocus = Color(0xFF3D5BFF);   // border.focus = brand-500
  static const Color inputBorderError = Color(0xFFE54B6B);   // border.error = error-500
  static const Color inputSuccess = Color(0xFF16B67A);        // text.success = success-500

  // Divider colors
  static const Color divider = Color(0xFFE5E7EB);

  // Status colors
  static const Color error = Color(0xFFEF4444);
  static const Color success = Color(0xFF10B981);

  // Social button colors
  static const Color googleBackground = Color(0xFFFFFFFF);
  static const Color buttonBorder = Color(0xFFE5E7EB);

  // Icon background colors (profile, settings, collections)
  static const Color iconBgBlue = Color(0xFFDBEAFE);
  static const Color iconBgPurple = Color(0xFFEDE9FE);
  static const Color iconBgYellow = Color(0xFFFEF3C7);
  static const Color iconBgGreen = Color(0xFFD1FAE5);
  static const Color iconBgOrange = Color(0xFFFFEDD5);
  static const Color iconBgPink = Color(0xFFFCE7F3);

  // Chip background colors (onboarding topic chips)
  static const Color chipTechNewsBg = Color(0xFFEBF0FE);
  static const Color chipGamesBg = Color(0xFFD1FAE5);
  static const Color chipGamesSelectedBg = Color(0xFF10B981);
  static const Color chipMoviesBg = Color(0xFFFEF3C7);
  static const Color chipLongReadsBg = Color(0xFFFCE7F3);
  static const Color chipAiMlBg = Color(0xFFEDE9FE);
  static const Color chipAiMlSelectedBg = Color(0xFF8B5CF6);

  // Article action bar colors
  static const Color actionDefault = Color(0xFF6B7280);
  static const Color actionLikeActive = Color(0xFF2B5CFC);
  static const Color actionSaveActive = Color(0xFF10B981);

  // Feed / collection badge colors
  static const Color likeCountBg = Color(0xFFF3F4F6);
  static const Color digestCardBg = Color(0xFFF7F7F9);
  static const Color itemCountBg = Color(0xFFEBF0FE);

  // Onboarding progress bar track
  static const Color progressTrack = Color(0xFFE5E7EB);

  // Selected count badge background
  static const Color selectedCountBg = Color(0xFFF3F4F6);
}
