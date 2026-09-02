import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../theme/colors.dart';

/// SVG icon atom from the Radar icon atlas (`assets/icons/*.svg`).
///
/// Mirrors the JSX `Icon({name, size, color})` API from
/// `design/reference/mockup/tokens.jsx`. Each SVG uses `currentColor` for
/// stroke / fill so the widget can tint it via `ColorFilter.mode(...,
/// BlendMode.srcIn)` without touching the source asset.
class NFIcon extends StatelessWidget {
  const NFIcon(
    this.name, {
    super.key,
    this.size = 22,
    this.color,
    this.semanticLabel,
  });

  final String name;
  final double size;
  final Color? color;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final Color resolved = color ?? NFColors.ink;
    return SvgPicture.asset(
      'assets/icons/$name.svg',
      width: size,
      height: size,
      colorFilter: ColorFilter.mode(resolved, BlendMode.srcIn),
      semanticsLabel: semanticLabel,
    );
  }
}
