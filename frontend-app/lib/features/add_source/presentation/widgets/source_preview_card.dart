import 'package:flutter/material.dart';
import '../../../../core/sources/presentation/widgets/source_type_badge.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/value_objects/source_input.dart';

class SourcePreviewCard extends StatelessWidget {
  const SourcePreviewCard({super.key, required this.parsed});

  final SourceInput parsed;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.inputBorder),
      ),
      child: Row(
        children: [
          SourceTypeBadge(type: parsed.type),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  parsed.displayName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textPrimary,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  _typeLabel(parsed.type.wire),
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textHint,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _typeLabel(String wire) {
    switch (wire) {
      case 'TELEGRAM':
        return 'Telegram-канал';
      case 'HABR':
        return 'Habr-хаб';
      case 'VCRU':
        return 'VC.RU';
    }
    return wire;
  }
}
