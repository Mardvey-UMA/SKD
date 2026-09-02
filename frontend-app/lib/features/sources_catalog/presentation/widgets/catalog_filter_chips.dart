import 'package:flutter/material.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../../../core/theme/app_colors.dart';

class CatalogFilterChips extends StatelessWidget {
  const CatalogFilterChips({
    super.key,
    required this.selected,
    required this.onChanged,
  });

  final SourceType? selected;
  final ValueChanged<SourceType?> onChanged;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 44,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          _Chip(
            label: 'Все',
            isSelected: selected == null,
            onTap: () => onChanged(null),
          ),
          const SizedBox(width: 8),
          _Chip(
            label: 'Telegram',
            isSelected: selected == SourceType.telegram,
            onTap: () => onChanged(SourceType.telegram),
          ),
          const SizedBox(width: 8),
          _Chip(
            label: 'Habr',
            isSelected: selected == SourceType.habr,
            onTap: () => onChanged(SourceType.habr),
          ),
          const SizedBox(width: 8),
          _Chip(
            label: 'VC.RU',
            isSelected: selected == SourceType.vcRu,
            onTap: () => onChanged(SourceType.vcRu),
          ),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({
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
        duration: const Duration(milliseconds: 160),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: isSelected ? AppColors.primary : AppColors.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? AppColors.primary : AppColors.inputBorder,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: isSelected ? Colors.white : AppColors.textPrimary,
          ),
        ),
      ),
    );
  }
}
