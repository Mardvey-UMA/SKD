import 'package:flutter/material.dart';
import '../../core/theme/app_colors.dart';

enum FieldStatus { none, success }

/// Token-aligned text field: neutral border at rest, blue halo on focus,
/// red border+halo only when errorText is non-null.
/// Pass [trailingStatus] to show a suffix status icon (e.g. success checkmark).
class CustomTextField extends StatefulWidget {
  const CustomTextField({
    super.key,
    required this.label,
    required this.controller,
    this.icon,
    this.obscureText = false,
    this.keyboardType = TextInputType.text,
    this.errorText,
    this.onChanged,
    this.onSubmitted,
    this.focusNode,
    this.textInputAction,
    this.autofocus = false,
    this.trailingStatus = FieldStatus.none,
  });

  final String label;
  final TextEditingController controller;
  final IconData? icon;
  final bool obscureText;
  final TextInputType keyboardType;
  final String? errorText;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final FocusNode? focusNode;
  final TextInputAction? textInputAction;
  final bool autofocus;
  final FieldStatus trailingStatus;

  @override
  State<CustomTextField> createState() => _CustomTextFieldState();
}

class _CustomTextFieldState extends State<CustomTextField> {
  late bool _obscureText;
  late FocusNode _focusNode;
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _obscureText = widget.obscureText;
    _focusNode = widget.focusNode ?? FocusNode();
    _focusNode.addListener(_onFocusChange);
  }

  void _onFocusChange() {
    setState(() {
      _isFocused = _focusNode.hasFocus;
    });
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    if (widget.focusNode == null) _focusNode.dispose();
    super.dispose();
  }

  Color get _borderColor {
    if (widget.errorText != null) return AppColors.inputBorderError;
    if (_isFocused) return AppColors.inputBorderFocus;
    return AppColors.inputBorder;
  }

  List<BoxShadow> get _halo {
    if (widget.errorText != null) {
      return [
        BoxShadow(
          color: AppColors.inputBorderError.withValues(alpha: 0.14),
          spreadRadius: 4,
          blurRadius: 0,
        ),
      ];
    }
    if (_isFocused) {
      return [
        BoxShadow(
          color: AppColors.inputBorderFocus.withValues(alpha: 0.16),
          spreadRadius: 4,
          blurRadius: 0,
        ),
      ];
    }
    return const [];
  }

  Widget? get _suffixIcon {
    // trailingStatus takes priority over obscureText toggle
    if (widget.trailingStatus == FieldStatus.success) {
      return AnimatedSwitcher(
        duration: const Duration(milliseconds: 150),
        child: Padding(
          key: const ValueKey('success'),
          padding: const EdgeInsets.only(right: 14),
          child: const Icon(
            Icons.check_circle,
            color: AppColors.inputSuccess,
            size: 18,
          ),
        ),
      );
    }
    if (widget.obscureText) {
      return IconButton(
        icon: Icon(
          _obscureText ? Icons.visibility_off : Icons.visibility,
          color: AppColors.textSecondary,
          size: 20,
        ),
        onPressed: () {
          setState(() {
            _obscureText = !_obscureText;
          });
        },
      );
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.label,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: AppColors.textPrimary,
          ),
        ),
        const SizedBox(height: 8),
        AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          decoration: BoxDecoration(
            color: AppColors.inputFill,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: _borderColor, width: 1.5),
            boxShadow: _halo,
          ),
          child: TextField(
            controller: widget.controller,
            obscureText: _obscureText,
            keyboardType: widget.keyboardType,
            onChanged: widget.onChanged,
            onSubmitted: widget.onSubmitted,
            focusNode: _focusNode,
            textInputAction: widget.textInputAction,
            autofocus: widget.autofocus,
            style: const TextStyle(
              fontSize: 16,
              color: AppColors.textPrimary,
              fontWeight: FontWeight.w400,
            ),
            decoration: InputDecoration(
              hintText: 'Введите ${widget.label.toLowerCase()}',
              hintStyle: const TextStyle(
                color: AppColors.textHint,
                fontSize: 16,
              ),
              prefixIcon: widget.icon != null
                  ? Icon(widget.icon, color: AppColors.textSecondary, size: 20)
                  : null,
              suffixIcon: _suffixIcon,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 18,
              ),
            ),
          ),
        ),
        if (widget.errorText != null) ...[
          const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Icon(Icons.info_outline, size: 14, color: AppColors.error),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  widget.errorText!,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.error,
                    height: 1.38,
                  ),
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}
