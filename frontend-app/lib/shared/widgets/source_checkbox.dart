import 'package:flutter/material.dart';
import '../../theme/app_tokens.dart';

class SourceCheckbox extends StatelessWidget {
  const SourceCheckbox({
    super.key,
    required this.value,
    required this.onChanged,
  });

  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => onChanged(!value),
      child: AnimatedContainer(
        duration: AppMotion.fast,
        curve: AppMotion.standard,
        width: 24,
        height: 24,
        decoration: BoxDecoration(
          color: value ? AppColors.interactivePrimary : Colors.transparent,
          borderRadius: AppRadii.bxs,
          border: value
              ? null
              : Border.all(color: AppColors.borderDefault, width: 1.5),
          boxShadow: value ? AppElevation.elev1 : null,
        ),
        child: value
            ? const Icon(Icons.check, color: Colors.white, size: 16)
            : null,
      ),
    );
  }
}
