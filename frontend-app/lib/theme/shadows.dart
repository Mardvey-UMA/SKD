import 'package:flutter/painting.dart';

/// Neo-futurism (`NF`) box-shadow tokens.
///
/// Each entry mirrors the CSS shadows from the mockup 1:1. CSS negative
/// spread (`-Npx`) becomes negative `spreadRadius` in Flutter.
class NFShadows {
  const NFShadows._();

  /// Card surface:
  /// `0 1px 0 rgba(0,0,0,0.02), 0 8px 24px -16px rgba(0,0,0,0.12)`.
  static const List<BoxShadow> card = <BoxShadow>[
    BoxShadow(
      offset: Offset(0, 1),
      blurRadius: 0,
      spreadRadius: 0,
      color: Color.fromRGBO(0, 0, 0, 0.02),
    ),
    BoxShadow(
      offset: Offset(0, 8),
      blurRadius: 24,
      spreadRadius: -16,
      color: Color.fromRGBO(0, 0, 0, 0.12),
    ),
  ];

  /// Floating bottom nav pill:
  /// `0 12px 30px -10px rgba(0,0,0,0.18)`.
  static const List<BoxShadow> bottomNav = <BoxShadow>[
    BoxShadow(
      offset: Offset(0, 12),
      blurRadius: 30,
      spreadRadius: -10,
      color: Color.fromRGBO(0, 0, 0, 0.18),
    ),
  ];

  /// Toast overlay:
  /// `0 20px 40px -10px rgba(0,0,0,0.4)`.
  static const List<BoxShadow> toast = <BoxShadow>[
    BoxShadow(
      offset: Offset(0, 20),
      blurRadius: 40,
      spreadRadius: -10,
      color: Color.fromRGBO(0, 0, 0, 0.4),
    ),
  ];

  /// Tablet panel (520-wide centered sheet):
  /// `0 24px 60px -20px rgba(0,0,0,0.18)`.
  static const List<BoxShadow> tabletPanel = <BoxShadow>[
    BoxShadow(
      offset: Offset(0, 24),
      blurRadius: 60,
      spreadRadius: -20,
      color: Color.fromRGBO(0, 0, 0, 0.18),
    ),
  ];
}
