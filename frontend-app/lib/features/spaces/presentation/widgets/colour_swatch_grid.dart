import 'package:flutter/material.dart';
import '../../../../core/sources/domain/space_color.dart';
import '../../../../theme/app_tokens.dart';

class ColourSwatchGrid extends StatelessWidget {
  const ColourSwatchGrid({
    super.key,
    required this.selected,
    required this.onChanged,
  });

  final SpaceColor selected;
  final ValueChanged<SpaceColor> onChanged;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        for (final variant in SpaceColor.values)
          _Swatch(
            variant: variant,
            isSelected: variant == selected,
            onTap: () => onChanged(variant),
          ),
      ],
    );
  }
}

class _Swatch extends StatefulWidget {
  const _Swatch({
    required this.variant,
    required this.isSelected,
    required this.onTap,
  });

  final SpaceColor variant;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  State<_Swatch> createState() => _SwatchState();
}

class _SwatchState extends State<_Swatch> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final color = widget.variant.accent.fg;
    final isSelected = widget.isSelected;

    return GestureDetector(
      onTap: widget.onTap,
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) => setState(() => _pressed = false),
      onTapCancel: () => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.94 : 1.0,
        duration: AppMotion.micro,
        curve: AppMotion.snappy,
        child: AnimatedContainer(
          duration: AppMotion.fast,
          curve: AppMotion.standard,
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: color,
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: color.withValues(alpha: 0.4),
                      blurRadius: 0,
                      spreadRadius: 3,
                    ),
                    ...AppElevation.elev2,
                  ]
                : AppElevation.elev1,
          ),
          child: isSelected
              ? Container(
                  margin: const EdgeInsets.all(3),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white,
                  ),
                  child: Center(
                    child: Container(
                      width: double.infinity,
                      height: double.infinity,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: color,
                      ),
                    ),
                  ),
                )
              : null,
        ),
      ),
    );
  }
}
