import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/theme_context_extension.dart';

/// 4-cell OTP input — §9.9 design tokens.
///
/// Uses a single hidden [TextField] driving four visible cells.
/// Keyboard appears on mount (autofocus). Supports paste.
class OtpInput extends StatefulWidget {
  const OtpInput({
    super.key,
    this.length = 4,
    required this.onCompleted,
    this.onChanged,
    this.hasError = false,
  });

  final int length;
  final ValueChanged<String> onCompleted;
  final ValueChanged<String>? onChanged;
  final bool hasError;

  @override
  State<OtpInput> createState() => _OtpInputState();
}

class _OtpInputState extends State<OtpInput> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _focusNode = FocusNode();
    _focusNode.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  String get _value => _controller.text;

  void _handleChange(String value) {
    setState(() {});
    widget.onChanged?.call(value);
    if (value.length == widget.length) {
      widget.onCompleted(value);
    }
  }

  @override
  Widget build(BuildContext context) {
    final hasFocus = _focusNode.hasFocus;

    return GestureDetector(
      onTap: () => _focusNode.requestFocus(),
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Hidden text field — drives all cells
          SizedBox(
            width: 0,
            height: 0,
            child: TextField(
              controller: _controller,
              focusNode: _focusNode,
              autofocus: true,
              keyboardType: TextInputType.number,
              maxLength: widget.length,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              onChanged: _handleChange,
              decoration: const InputDecoration(counterText: ''),
              style: const TextStyle(color: Colors.transparent, fontSize: 1),
              cursorColor: Colors.transparent,
              cursorWidth: 0,
              enableInteractiveSelection: false,
            ),
          ),

          // Visible cells row — shrink cell size for longer codes
          LayoutBuilder(
            builder: (context, constraints) {
              final maxWidth = constraints.maxWidth > 0
                  ? constraints.maxWidth
                  : MediaQuery.of(context).size.width - 64;
              final spacing = widget.length > 4 ? 6.0 : 8.0;
              final cellWidth =
                  ((maxWidth - spacing * (widget.length - 1)) / widget.length)
                      .clamp(36.0, 56.0);
              final cellHeight = cellWidth * (64 / 56);

              return Row(
                mainAxisAlignment: MainAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: List.generate(widget.length, (i) {
                  final digit = i < _value.length ? _value[i] : null;
                  final isActive = hasFocus &&
                      (i == _value.length ||
                          (i == widget.length - 1 &&
                              _value.length == widget.length));
                  return Padding(
                    padding: EdgeInsets.only(
                      right: i < widget.length - 1 ? spacing : 0,
                    ),
                    child: _OtpCell(
                      digit: digit,
                      isActive: isActive,
                      hasError: widget.hasError,
                      width: cellWidth,
                      height: cellHeight,
                    ),
                  );
                }),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _OtpCell extends StatelessWidget {
  const _OtpCell({
    required this.digit,
    required this.isActive,
    required this.hasError,
    this.width = 56,
    this.height = 64,
  });

  final String? digit;
  final bool isActive;
  final bool hasError;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final t = context.typography;

    final Color borderColor;
    final List<BoxShadow> shadows;

    if (hasError) {
      borderColor = c.border.error;
      shadows = [
        BoxShadow(
          color: c.border.errorHalo,
          spreadRadius: 4,
          blurRadius: 0,
        ),
      ];
    } else if (isActive) {
      borderColor = c.border.focus;
      shadows = [
        BoxShadow(
          color: c.border.focusHalo,
          spreadRadius: 4,
          blurRadius: 0,
        ),
      ];
    } else if (digit != null) {
      borderColor = c.border.focus.withValues(alpha: 0.4);
      shadows = const [];
    } else {
      borderColor = c.border.dfault;
      shadows = const [];
    }

    return AnimatedContainer(
      duration: AppMotion.fast,
      curve: AppMotion.standard,
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: c.surface.base,
        borderRadius: AppRadii.bmd,
        border: Border.all(color: borderColor, width: 1.5),
        boxShadow: shadows,
      ),
      child: Center(
        child: digit != null
            ? Text(
                digit!,
                style: t.headingL.copyWith(color: c.text.primary),
              )
            : const SizedBox.shrink(),
      ),
    );
  }
}
