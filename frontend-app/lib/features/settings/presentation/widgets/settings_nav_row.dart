import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

class SettingsNavRow extends StatelessWidget {
  const SettingsNavRow({
    super.key,
    required this.icon,
    required this.iconBackgroundColor,
    required this.title,
    this.trailingValue,
    this.onTap,
    this.titleColor,
  });

  final IconData icon;
  final Color iconBackgroundColor;
  final String title;
  final String? trailingValue;
  final VoidCallback? onTap;
  final Color? titleColor;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 56,
        color: AppColors.surface,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: iconBackgroundColor,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                icon,
                size: 18,
                color: titleColor ?? AppColors.textPrimary,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                title,
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: titleColor ?? AppColors.textPrimary,
                ),
              ),
            ),
            if (trailingValue != null)
              Text(
                trailingValue!,
                style: const TextStyle(fontSize: 13, color: AppColors.textHint),
              ),
            if (trailingValue == null || onTap != null) ...[
              const SizedBox(width: 4),
              if (titleColor == null)
                const Icon(
                  Icons.chevron_right,
                  size: 20,
                  color: AppColors.textHint,
                ),
            ],
          ],
        ),
      ),
    );
  }
}
