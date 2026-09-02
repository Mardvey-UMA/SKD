import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../theme/typography.dart';

/// Neo-futurism OTP input atom.
///
/// A single hidden [TextField] drives [length] visible cells. Designed to
/// repaint **only** the cell row when the user types — the surrounding
/// screen never rebuilds.
///
/// Performance notes:
/// * The hidden field's controller is wrapped in a [ValueListenableBuilder]
///   so per-keystroke change notifications localise to the cell row — no
///   parent [setState] required.
/// * [onChanged] / [onCompleted] fire from the controller listener, not
///   from `TextField.onChanged`, so callers can drive verification state
///   without triggering cascading rebuilds.
/// * Cells use plain [Container] (no [AnimatedContainer]) — border colour
///   flips instantly on focus / error / fill. The animated focus ring
///   glow is gated via [hasError]/[isFocused] booleans captured by the
///   same builder, avoiding an implicit animation per cell.
class NFOtpInput extends StatefulWidget {
  const NFOtpInput({
    super.key,
    this.length = 6,
    required this.onCompleted,
    this.onChanged,
    this.hasError = false,
    this.autofocus = true,
  });

  final int length;
  final ValueChanged<String> onCompleted;
  final ValueChanged<String>? onChanged;
  final bool hasError;
  final bool autofocus;

  @override
  State<NFOtpInput> createState() => _NFOtpInputState();
}

class _NFOtpInputState extends State<NFOtpInput> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;
  String _lastValue = '';

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _focusNode = FocusNode();
    _controller.addListener(_onControllerChange);
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChange);
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _onControllerChange() {
    final v = _controller.text;
    if (v == _lastValue) return;
    _lastValue = v;
    widget.onChanged?.call(v);
    if (v.length == widget.length) {
      widget.onCompleted(v);
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: _focusNode.requestFocus,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Hidden TextField that captures keystrokes.
          SizedBox(
            width: 0,
            height: 0,
            child: TextField(
              controller: _controller,
              focusNode: _focusNode,
              autofocus: widget.autofocus,
              keyboardType: TextInputType.number,
              maxLength: widget.length,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              enableInteractiveSelection: false,
              cursorColor: Colors.transparent,
              cursorWidth: 0,
              style: const TextStyle(color: Colors.transparent, fontSize: 1),
              decoration: const InputDecoration(
                counterText: '',
                filled: false,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                errorBorder: InputBorder.none,
                focusedErrorBorder: InputBorder.none,
                disabledBorder: InputBorder.none,
                contentPadding: EdgeInsets.zero,
                isCollapsed: true,
              ),
            ),
          ),

          _OtpCells(
            controller: _controller,
            focusNode: _focusNode,
            length: widget.length,
            hasError: widget.hasError,
          ),
        ],
      ),
    );
  }
}

/// Isolated row of cells — rebuilds only on controller / focus changes.
class _OtpCells extends StatelessWidget {
  const _OtpCells({
    required this.controller,
    required this.focusNode,
    required this.length,
    required this.hasError,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final int length;
  final bool hasError;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([controller, focusNode]),
      builder: (context, _) {
        final value = controller.text;
        final hasFocus = focusNode.hasFocus;

        return LayoutBuilder(
          builder: (context, constraints) {
            final maxWidth = constraints.maxWidth > 0
                ? constraints.maxWidth
                : MediaQuery.of(context).size.width - 64;
            final spacing = length > 4 ? 6.0 : 8.0;
            final cellWidth =
                ((maxWidth - spacing * (length - 1)) / length)
                    .clamp(36.0, 56.0);
            final cellHeight = cellWidth * (60 / 52);

            return Row(
              mainAxisAlignment: MainAxisAlignment.center,
              mainAxisSize: MainAxisSize.min,
              children: List.generate(length, (i) {
                final digit = i < value.length ? value[i] : null;
                final isActive = hasFocus &&
                    (i == value.length ||
                        (i == length - 1 && value.length == length));
                return Padding(
                  padding: EdgeInsets.only(
                    right: i < length - 1 ? spacing : 0,
                  ),
                  child: _OtpCell(
                    digit: digit,
                    isActive: isActive,
                    hasError: hasError,
                    width: cellWidth,
                    height: cellHeight,
                  ),
                );
              }),
            );
          },
        );
      },
    );
  }
}

class _OtpCell extends StatelessWidget {
  const _OtpCell({
    required this.digit,
    required this.isActive,
    required this.hasError,
    required this.width,
    required this.height,
  });

  final String? digit;
  final bool isActive;
  final bool hasError;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final Color borderColor;
    final double borderWidth;
    if (hasError) {
      borderColor = NFColors.warn;
      borderWidth = 1.4;
    } else if (isActive) {
      borderColor = NFColors.ink;
      borderWidth = 1.4;
    } else if (digit != null) {
      borderColor = NFColors.ink;
      borderWidth = 1.0;
    } else {
      borderColor = NFColors.hairline;
      borderWidth = 1.0;
    }

    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(NFRadii.radiusSm),
        border: Border.all(color: borderColor, width: borderWidth),
      ),
      alignment: Alignment.center,
      child: digit == null
          ? null
          : Text(
              digit!,
              style: NFTypography.h1.copyWith(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: NFColors.ink,
              ),
            ),
    );
  }
}
