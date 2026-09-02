import 'package:flutter/material.dart';

/// Border radius scale from §4.1 of design-system.md.
class AppRadii {
  AppRadii._();

  /// 8 — Mini badges, checkboxes
  static const Radius xs = Radius.circular(8);

  /// 12 — Inner icon tiles within large cards
  static const Radius sm = Radius.circular(12);

  /// 16 — Input fields, settings list items
  static const Radius md = Radius.circular(16);

  /// 20 — Feed cards, primary buttons, profile icon tiles
  static const Radius lg = Radius.circular(20);

  /// 28 — Hero cards, bottom-sheet top edge
  static const Radius xl = Radius.circular(28);

  /// 36 — Modals, floating containers
  static const Radius xl2 = Radius.circular(36);

  /// 9999 — Chips, avatars, pill elements
  static const Radius full = Radius.circular(9999);

  // Convenience BorderRadius constants
  static const BorderRadius bxs = BorderRadius.all(xs);
  static const BorderRadius bsm = BorderRadius.all(sm);
  static const BorderRadius bmd = BorderRadius.all(md);
  static const BorderRadius blg = BorderRadius.all(lg);
  static const BorderRadius bxl = BorderRadius.all(xl);
  static const BorderRadius bxl2 = BorderRadius.all(xl2);
  static const BorderRadius bfull = BorderRadius.all(full);
}
