import 'package:flutter/material.dart';
import '../../domain/source_type.dart';

/// Compact pill badge displaying the source type (TG / Habr / VC).
class SourceTypeBadge extends StatelessWidget {
  const SourceTypeBadge({super.key, required this.type});

  final SourceType? type;

  @override
  Widget build(BuildContext context) {
    if (type == null) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        decoration: BoxDecoration(
          color: const Color(0xFFF0F0F0),
          borderRadius: BorderRadius.circular(4),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.help_outline, size: 12, color: Color(0xFF999999)),
            SizedBox(width: 4),
            Text(
              '?',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: Color(0xFF999999),
                height: 1.0,
              ),
            ),
          ],
        ),
      );
    }
    final palette = _palette(type!);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: palette.background,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(palette.icon, size: 12, color: palette.foreground),
          const SizedBox(width: 4),
          Text(
            type!.shortLabel,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: palette.foreground,
              height: 1.0,
            ),
          ),
        ],
      ),
    );
  }

  _BadgePalette _palette(SourceType t) {
    switch (t) {
      case SourceType.telegram:
        return const _BadgePalette(
          background: Color(0xFFE8F4FD),
          foreground: Color(0xFF0088CC),
          icon: Icons.send,
        );
      case SourceType.habr:
        return const _BadgePalette(
          background: Color(0xFFE8F1F0),
          foreground: Color(0xFF65A3BE),
          icon: Icons.code,
        );
      case SourceType.vcRu:
        return const _BadgePalette(
          background: Color(0xFFFFF0E8),
          foreground: Color(0xFFF24E1E),
          icon: Icons.business_center_outlined,
        );
    }
  }
}

class _BadgePalette {
  const _BadgePalette({
    required this.background,
    required this.foreground,
    required this.icon,
  });

  final Color background;
  final Color foreground;
  final IconData icon;
}
