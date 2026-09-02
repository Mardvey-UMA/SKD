import 'package:flutter/material.dart';

enum AccentVariant {
  sky,
  violet,
  rose,
  amber,
  mint,
  coral;

  Color get bg => switch (this) {
    AccentVariant.sky => const Color(0xFFE8F0FF),
    AccentVariant.violet => const Color(0xFFECE7FF),
    AccentVariant.rose => const Color(0xFFFFE5EF),
    AccentVariant.amber => const Color(0xFFFFF2D8),
    AccentVariant.mint => const Color(0xFFE0F7EE),
    AccentVariant.coral => const Color(0xFFFFE4DE),
  };

  Color get fg => switch (this) {
    AccentVariant.sky => const Color(0xFFBCD4FF),
    AccentVariant.violet => const Color(0xFFC9BEFF),
    AccentVariant.rose => const Color(0xFFFFB8D1),
    AccentVariant.amber => const Color(0xFFFFD88A),
    AccentVariant.mint => const Color(0xFF9FE8CB),
    AccentVariant.coral => const Color(0xFFFF9B8A),
  };
}

class IconTile extends StatelessWidget {
  const IconTile({
    super.key,
    required this.icon,
    this.size = 32,
    this.iconSize = 18,
    this.variant = AccentVariant.violet,
    this.borderRadius = 16,
  });

  final IconData icon;
  final double size;
  final double iconSize;
  final AccentVariant variant;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: variant.bg,
        borderRadius: BorderRadius.circular(borderRadius),
      ),
      child: Icon(icon, size: iconSize, color: variant.fg),
    );
  }
}
