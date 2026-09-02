import 'package:flutter/material.dart';

/// Elevation / shadow tokens from §6.2 of design-system.md.
///
/// Alpha calculation reference:
///   rgba(15,24,40, 0.04) → alpha = round(0.04*255) = 10 = 0x0A
///   rgba(15,24,40, 0.03) → alpha = round(0.03*255) = 8  = 0x08
///   rgba(15,24,40, 0.06) → alpha = round(0.06*255) = 15 = 0x0F
///   rgba(15,24,40, 0.04) → 0x0A  (reused for elev-2 second shadow)
///   rgba(15,24,40, 0.08) → alpha = round(0.08*255) = 20 = 0x14
///   rgba(61,91,255,0.08) → 0x14
///   rgba(15,24,40, 0.12) → alpha = round(0.12*255) = 31 = 0x1F
///   rgba(61,91,255,0.10) → alpha = round(0.10*255) = 26 = 0x1A
class AppElevation {
  AppElevation._();

  /// No shadow — flat containers, grouped list items
  static const List<BoxShadow> elev0 = [];

  /// Subtle touching shadow — regular cards, input fields
  static const List<BoxShadow> elev1 = [
    BoxShadow(
      color: Color(0x0A0F1828), // rgba(15,24,40, 0.04)
      offset: Offset(0, 1),
      blurRadius: 2,
    ),
    BoxShadow(
      color: Color(0x080F1828), // rgba(15,24,40, 0.03)
      offset: Offset(0, 2),
      blurRadius: 6,
    ),
  ];

  /// Medium floating shadow — feed cards, selected swatches
  static const List<BoxShadow> elev2 = [
    BoxShadow(
      color: Color(0x0F0F1828), // rgba(15,24,40, 0.06)
      offset: Offset(0, 4),
      blurRadius: 12,
    ),
    BoxShadow(
      color: Color(0x0A0F1828), // rgba(15,24,40, 0.04)
      offset: Offset(0, 12),
      blurRadius: 32,
    ),
  ];

  /// Prominent floating shadow — FAB, active card, CTA buttons
  static const List<BoxShadow> elev3 = [
    BoxShadow(
      color: Color(0x140F1828), // rgba(15,24,40, 0.08)
      offset: Offset(0, 8),
      blurRadius: 24,
    ),
    BoxShadow(
      color: Color(0x143D5BFF), // rgba(61,91,255, 0.08) — brand color glow
      offset: Offset(0, 20),
      blurRadius: 48,
    ),
  ];

  /// Strong overlay shadow — bottom-sheets, modals
  static const List<BoxShadow> elev4 = [
    BoxShadow(
      color: Color(0x1F0F1828), // rgba(15,24,40, 0.12)
      offset: Offset(0, 16),
      blurRadius: 40,
    ),
    BoxShadow(
      color: Color(0x1A3D5BFF), // rgba(61,91,255, 0.10) — brand color glow
      offset: Offset(0, 32),
      blurRadius: 80,
    ),
  ];

  // §6.3 Glass surfaces
  /// Bottom navigation glass shadow (upward)
  static const List<BoxShadow> glassNav = [
    BoxShadow(
      color: Color(0x0F0F1828), // rgba(15,24,40, 0.06)
      offset: Offset(0, -8),
      blurRadius: 32,
    ),
  ];

  /// Modal glass shadow — same as elev4
  static const List<BoxShadow> glassModal = elev4;
}
