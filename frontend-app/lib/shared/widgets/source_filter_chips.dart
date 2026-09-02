import 'package:flutter/material.dart';
import '../../core/sources/domain/source_type.dart';
import '../../theme/app_tokens.dart';

class SourceFilterChips extends StatelessWidget {
  const SourceFilterChips({
    super.key,
    required this.selected,
    required this.onChanged,
  });

  final SourceType? selected;
  final ValueChanged<SourceType?> onChanged;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          _FilterChip(
            label: 'Все',
            isSelected: selected == null,
            onTap: () => onChanged(null),
          ),
          const SizedBox(width: AppSpacing.xs),
          _FilterChip(
            label: 'Telegram',
            isSelected: selected == SourceType.telegram,
            onTap: () => onChanged(SourceType.telegram),
          ),
          const SizedBox(width: AppSpacing.xs),
          _FilterChip(
            label: 'Habr',
            isSelected: selected == SourceType.habr,
            onTap: () => onChanged(SourceType.habr),
          ),
          const SizedBox(width: AppSpacing.xs),
          _FilterChip(
            label: 'VC.RU',
            isSelected: selected == SourceType.vcRu,
            onTap: () => onChanged(SourceType.vcRu),
          ),
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: AppMotion.fast,
        curve: AppMotion.standard,
        height: 36,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          gradient: isSelected ? AppGradients.ctaPrimary : null,
          color: isSelected ? null : AppColors.surfaceBase,
          borderRadius: AppRadii.bfull,
          border: isSelected
              ? null
              : Border.all(color: AppColors.borderDefault, width: 1),
        ),
        child: Text(
          label,
          style: AppTypography.caption.copyWith(
            fontWeight: FontWeight.w600,
            color: isSelected ? AppColors.textOnPrimary : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }
}
