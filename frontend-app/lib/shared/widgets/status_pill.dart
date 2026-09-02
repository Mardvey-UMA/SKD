import 'package:flutter/material.dart';
import 'icon_tile.dart';
import '../../theme/app_tokens.dart';

class StatusPill extends StatelessWidget {
  const StatusPill({
    super.key,
    required this.label,
    required this.variant,
    required this.icon,
  });

  final String label;
  final AccentVariant variant;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;

    final textColor = variant == AccentVariant.amber
        ? const Color(0xFF9B6B00)
        : tokens.colors.text.secondary;

    return Container(
      height: 28,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: variant.bg,
        borderRadius: AppRadii.bfull,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: textColor),
          const SizedBox(width: 4),
          Text(
            label,
            style: tokens.typography.caption.copyWith(color: textColor),
          ),
        ],
      ),
    );
  }
}
