import 'package:flutter/material.dart';
import '../../../../core/config/feature_flags.dart';
import '../../../../core/theme/app_colors.dart';

class SpaceLimitBanner extends StatelessWidget {
  const SpaceLimitBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.iconBgYellow,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline,
              color: AppColors.textPrimary, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Достигнут лимит пространств (${FeatureFlags.maxSpaces}). '
              'Удалите одно, чтобы создать новое.',
              style: const TextStyle(
                  fontSize: 12, color: AppColors.textPrimary),
            ),
          ),
        ],
      ),
    );
  }
}
