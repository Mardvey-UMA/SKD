import 'package:flutter/painting.dart';

/// Neo-futurism (`NF`) corner radii.
///
/// Mirrors `tokens.jsx`:
/// * `radius`   = 18  — default card/pill radius
/// * `radiusLg` = 26  — large surfaces (big cards, onboarding panel)
/// * `radiusSm` = 10  — chips, small pills
class NFRadii {
  const NFRadii._();

  static const double radius = 18;
  static const double radiusLg = 26;
  static const double radiusSm = 10;

  static const BorderRadius br = BorderRadius.all(Radius.circular(radius));
  static const BorderRadius brLg = BorderRadius.all(Radius.circular(radiusLg));
  static const BorderRadius brSm = BorderRadius.all(Radius.circular(radiusSm));
}
