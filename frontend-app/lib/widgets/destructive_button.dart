import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

class DestructiveButton extends StatefulWidget {
  const DestructiveButton({
    super.key,
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final VoidCallback onPressed;

  @override
  State<DestructiveButton> createState() => _DestructiveButtonState();
}

class _DestructiveButtonState extends State<DestructiveButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final errorColor = tokens.colors.status.error;

    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) {
        setState(() => _pressed = false);
        widget.onPressed();
      },
      onTapCancel: () => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.97 : 1.0,
        duration: const Duration(milliseconds: 100),
        child: Container(
          height: 52,
          width: double.infinity,
          decoration: BoxDecoration(
            color: tokens.colors.surface.base,
            border: Border.all(
              color: errorColor.withValues(alpha: 0.2),
              width: 1,
            ),
            borderRadius: AppRadii.blg,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.delete_outline, color: errorColor, size: 20),
              const SizedBox(width: 8),
              Text(
                widget.label,
                style: tokens.typography.button.copyWith(color: errorColor),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
