import 'package:flutter/material.dart';
import '../../../shared/widgets/icon_tile.dart';

enum SpaceColor {
  red('RED', AccentVariant.coral),
  orange('ORANGE', AccentVariant.amber),
  yellow('YELLOW', AccentVariant.amber),
  green('GREEN', AccentVariant.mint),
  teal('TEAL', AccentVariant.sky),
  blue('BLUE', AccentVariant.sky),
  purple('PURPLE', AccentVariant.violet),
  pink('PINK', AccentVariant.rose);

  const SpaceColor(this.wire, this.accent);

  final String wire;
  final AccentVariant accent;

  /// Legacy `material` colour kept for backward compat — routes to accent.fg.
  Color get material => accent.fg;

  static SpaceColor fromWire(String? raw) {
    if (raw == null) return SpaceColor.blue;
    final upper = raw.toUpperCase();
    for (final c in values) {
      if (c.wire == upper) return c;
    }
    return SpaceColor.blue;
  }
}
