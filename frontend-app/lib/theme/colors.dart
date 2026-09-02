import 'package:flutter/painting.dart';

/// Neo-futurism (`NF`) color palette.
///
/// Mirrors `design/reference/mockup/tokens.jsx` 1:1. Tokens are explicit —
/// NO Material 3 seed generation. Each entry in `radar-redesign-prompts.md`
/// § «Design tokens (reference table)» maps to a single `const Color` here.
class NFColors {
  const NFColors._();

  /// App background.
  static const Color bg = Color(0xFFF4F5F2);

  /// Cards, sheets.
  static const Color surface = Color(0xFFFFFFFF);

  /// Muted surface — cancel rows, hero blocks.
  static const Color surface2 = Color(0xFFECEDE8);

  /// 1px dividers & card borders: `rgba(17,17,17,0.09)`.
  static const Color hairline = Color.fromRGBO(17, 17, 17, 0.09);

  /// Primary text, primary buttons.
  static const Color ink = Color(0xFF0E0F0D);

  /// Body copy on light backgrounds.
  static const Color ink2 = Color(0xFF2A2B28);

  /// Secondary text, meta.
  static const Color mute = Color(0xFF6E6F6A);

  /// Tertiary (3px dot separators).
  static const Color mute2 = Color(0xFF9A9B96);

  /// Accent fill (liked state, brand accents).
  static const Color accent = Color(0xFF3B2BFF);

  /// Foreground on accent.
  static const Color accentInk = Color(0xFFFFFFFF);

  /// Secondary accent (bookmark fill, premium).
  static const Color lime = Color(0xFFCCFF33);

  /// Foreground on lime.
  static const Color limeInk = Color(0xFF0E0F0D);

  /// Destructive, hide-source.
  static const Color warn = Color(0xFFFF5A1F);

  /// Chip background.
  static const Color chipBg = Color(0xFFE8E9E3);
}
