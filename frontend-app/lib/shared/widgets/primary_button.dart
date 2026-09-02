import 'package:flutter/material.dart';
import '../../core/theme/app_colors.dart';

/// Primary CTA button with gradient.ctaPrimary and press-scale feedback (§9.2).
class PrimaryButton extends StatefulWidget {
  const PrimaryButton({
    super.key,
    required this.text,
    required this.onPressed,
    this.isLoading = false,
    this.isEnabled = true,
  });

  final String text;
  final VoidCallback? onPressed;
  final bool isLoading;
  final bool isEnabled;

  @override
  State<PrimaryButton> createState() => _PrimaryButtonState();
}

class _PrimaryButtonState extends State<PrimaryButton> {
  bool _pressed = false;

  bool get _active => widget.isEnabled && !widget.isLoading;

  void _onTapDown(TapDownDetails _) {
    if (_active) setState(() => _pressed = true);
  }

  void _onTapUp(TapUpDetails _) {
    if (_active) {
      setState(() => _pressed = false);
      widget.onPressed?.call();
    }
  }

  void _onTapCancel() => setState(() => _pressed = false);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: _onTapDown,
      onTapUp: _onTapUp,
      onTapCancel: _onTapCancel,
      child: AnimatedScale(
        scale: _pressed ? 0.97 : 1.0,
        duration: const Duration(milliseconds: 120),
        curve: Curves.easeOut,
        child: SizedBox(
          width: double.infinity,
          height: 56,
          child: AnimatedOpacity(
            opacity: _active ? 1.0 : 0.4,
            duration: const Duration(milliseconds: 120),
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFF3D5BFF), Color(0xFF7364FF)],
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: _active
                    ? [
                        BoxShadow(
                          color: const Color(0xFF3D5BFF).withValues(alpha: 0.28),
                          offset: const Offset(0, 8),
                          blurRadius: 24,
                        ),
                      ]
                    : [],
              ),
              child: Center(
                child: widget.isLoading
                    ? const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor:
                              AlwaysStoppedAnimation<Color>(Colors.white),
                        ),
                      )
                    : Text(
                        widget.text,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                          letterSpacing: 0.004 * 15,
                        ),
                      ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Secondary ghost button: white bg + 1.5px border, no gradient (§9.2).
class SecondaryButton extends StatefulWidget {
  const SecondaryButton({
    super.key,
    required this.text,
    required this.onPressed,
    this.isLoading = false,
    this.isEnabled = true,
  });

  final String text;
  final VoidCallback? onPressed;
  final bool isLoading;
  final bool isEnabled;

  @override
  State<SecondaryButton> createState() => _SecondaryButtonState();
}

class _SecondaryButtonState extends State<SecondaryButton> {
  bool _pressed = false;

  bool get _active => widget.isEnabled && !widget.isLoading;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) {
        if (_active) setState(() => _pressed = true);
      },
      onTapUp: (_) {
        if (_active) {
          setState(() => _pressed = false);
          widget.onPressed?.call();
        }
      },
      onTapCancel: () => setState(() => _pressed = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 120),
        width: double.infinity,
        height: 56,
        decoration: BoxDecoration(
          color: _pressed
              ? const Color(0xFFF5F7FC) // surface.backgroundSubtle
              : AppColors.surface,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: const Color(0x1A0F1828), // border.default rgba(15,24,40,0.10)
            width: 1.5,
          ),
        ),
        child: Center(
          child: widget.isLoading
              ? const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor:
                        AlwaysStoppedAnimation<Color>(AppColors.textPrimary),
                  ),
                )
              : Text(
                  widget.text,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textPrimary,
                    letterSpacing: 0.004 * 15,
                  ),
                ),
        ),
      ),
    );
  }
}

/// Social sign-in button (Google).
class SocialButton extends StatelessWidget {
  const SocialButton({
    super.key,
    required this.text,
    required this.icon,
    required this.onPressed,
  });

  final String text;
  final Widget icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          backgroundColor: AppColors.surface,
          side: const BorderSide(color: AppColors.buttonBorder, width: 1),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            icon,
            const SizedBox(width: 12),
            Text(
              text,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
