import 'package:flutter/material.dart';

import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../theme/typography.dart';
import 'nf_icon.dart';

/// Neo-futurism input atom.
///
/// Mirrors the `Input` component in `design/reference/mockup/onboarding.jsx`
/// and collection-editor inputs in `screens.jsx`. Surface-white container,
/// hairline border, optional leading icon (SVG name from the Radar atlas),
/// optional trailing counter. Three states: default / focused / error.
///
/// Sizing per Prompt 22:
/// * padding `EdgeInsets.symmetric(horizontal: 16, vertical: 18)`
/// * radius `NFRadii.radiusSm` (10) default, `NFRadii.radius` (18) for pill
class NFInput extends StatefulWidget {
  const NFInput({
    super.key,
    required this.controller,
    this.placeholder,
    this.icon,
    this.obscureText = false,
    this.keyboardType,
    this.textInputAction,
    this.onChanged,
    this.onSubmitted,
    this.errorText,
    this.maxLength,
    this.showCounter = false,
    this.radius = NFRadii.radiusSm,
    this.autofillHints,
  });

  final TextEditingController controller;
  final String? placeholder;

  /// Optional leading SVG icon name from `assets/icons/*.svg`.
  final String? icon;

  final bool obscureText;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  /// Error message shown below the field — switches visual state to error.
  final String? errorText;

  final int? maxLength;
  final bool showCounter;

  /// Corner radius — default chip-style (`radiusSm = 10`). Use
  /// [NFRadii.radius] (18) for pill inputs.
  final double radius;

  final Iterable<String>? autofillHints;

  @override
  State<NFInput> createState() => _NFInputState();
}

class _NFInputState extends State<NFInput> {
  late final FocusNode _focusNode;
  bool _focused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
    _focusNode.addListener(_onFocusChange);
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    _focusNode.dispose();
    super.dispose();
  }

  void _onFocusChange() {
    if (_focused != _focusNode.hasFocus) {
      setState(() => _focused = _focusNode.hasFocus);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool hasError = widget.errorText != null;
    final Color borderColor = hasError
        ? NFColors.warn
        : (_focused ? NFColors.ink : NFColors.hairline);
    final double borderWidth = hasError || _focused ? 1.4 : 1.0;
    final bool hasCounter = widget.showCounter && widget.maxLength != null;

    final field = Container(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(widget.radius),
        border: Border.all(color: borderColor, width: borderWidth),
      ),
      padding: EdgeInsets.symmetric(
        horizontal: 16,
        // Tighter vertical padding when an icon is present so hit-height
        // stays ~52px; Input-spec padding is `18` on plain fields.
        vertical: widget.icon != null ? 14 : 18,
      ),
      child: Row(
        children: [
          if (widget.icon != null) ...[
            NFIcon(widget.icon!, size: 17, color: NFColors.mute),
            const SizedBox(width: 10),
          ],
          Expanded(
            child: TextField(
              controller: widget.controller,
              focusNode: _focusNode,
              obscureText: widget.obscureText,
              keyboardType: widget.keyboardType,
              textInputAction: widget.textInputAction,
              onChanged: widget.onChanged,
              onSubmitted: widget.onSubmitted,
              maxLength: widget.maxLength,
              autofillHints: widget.autofillHints,
              style: NFTypography.h2.copyWith(
                fontSize: 15,
                fontWeight: FontWeight.w500,
                letterSpacing: 0,
                color: NFColors.ink,
              ),
              decoration: InputDecoration(
                hintText: widget.placeholder,
                hintStyle: NFTypography.body.copyWith(color: NFColors.mute),
                isCollapsed: true,
                // Override global InputDecorationTheme so the outer NFInput
                // container is the ONLY border visible — otherwise Flutter
                // draws filled background + OutlineInputBorder from the
                // RadarTheme around the inner TextField, producing a visible
                // square/pill inside our pill (see radar_theme.dart).
                filled: false,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                errorBorder: InputBorder.none,
                focusedErrorBorder: InputBorder.none,
                disabledBorder: InputBorder.none,
                counterText: '',
                contentPadding: EdgeInsets.zero,
              ),
            ),
          ),
          if (hasCounter)
            Padding(
              padding: const EdgeInsets.only(left: 12),
              child: AnimatedBuilder(
                animation: widget.controller,
                builder: (_, _) => Text(
                  '${widget.controller.text.length}/${widget.maxLength}',
                  style: NFTypography.mono,
                ),
              ),
            ),
        ],
      ),
    );

    if (!hasError) return field;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        field,
        const SizedBox(height: 6),
        Padding(
          padding: const EdgeInsets.only(left: 4),
          child: Text(
            widget.errorText!,
            style: NFTypography.mono.copyWith(color: NFColors.warn),
          ),
        ),
      ],
    );
  }
}
