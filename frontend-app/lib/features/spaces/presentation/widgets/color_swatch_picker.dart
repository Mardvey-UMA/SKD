import 'package:flutter/material.dart';
import '../../../../core/sources/domain/space_color.dart';
import '../../../../core/theme/app_colors.dart';

class ColorSwatchPicker extends StatelessWidget {
  const ColorSwatchPicker({
    super.key,
    required this.selected,
    required this.onSelected,
  });

  final SpaceColor selected;
  final ValueChanged<SpaceColor> onSelected;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: [
        for (final color in SpaceColor.values)
          GestureDetector(
            onTap: () => onSelected(color),
            child: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: color.material,
                border: Border.all(
                  color: selected == color
                      ? AppColors.textPrimary
                      : Colors.transparent,
                  width: 3,
                ),
              ),
              child: selected == color
                  ? const Icon(Icons.check, color: Colors.white, size: 18)
                  : null,
            ),
          ),
      ],
    );
  }
}
