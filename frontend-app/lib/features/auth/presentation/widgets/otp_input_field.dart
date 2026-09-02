import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/theme/app_colors.dart';

/// 6-digit OTP input widget with auto-focus advance, backspace handling, and paste support.
class OtpInputField extends StatefulWidget {
  const OtpInputField({
    super.key,
    this.length = 6,
    required this.onChanged,
    required this.onCompleted,
    this.hasError = false,
    this.enabled = true,
    this.focusNode,
  });

  final int length;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onCompleted;
  final bool hasError;
  final bool enabled;
  final FocusNode? focusNode;

  @override
  State<OtpInputField> createState() => _OtpInputFieldState();
}

class _OtpInputFieldState extends State<OtpInputField> {
  late final List<TextEditingController> _controllers;
  late final List<FocusNode> _focusNodes;

  @override
  void initState() {
    super.initState();
    _controllers = List.generate(widget.length, (_) => TextEditingController());
    _focusNodes = List.generate(widget.length, (i) {
      final node = FocusNode();
      if (i == 0 && widget.focusNode != null) {
        // First node uses provided focusNode — attach listener
      }
      return node;
    });
  }

  @override
  void dispose() {
    for (final c in _controllers) {
      c.dispose();
    }
    for (final f in _focusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  String get _currentValue =>
      _controllers.map((c) => c.text).join();

  void _onCellChanged(int index, String value) {
    if (value.length > 1) {
      // Handle paste — distribute across cells
      _distributePaste(value);
      return;
    }

    if (value.isNotEmpty && index < widget.length - 1) {
      _focusNodes[index + 1].requestFocus();
    }

    final full = _currentValue;
    widget.onChanged(full);
    if (full.length == widget.length) {
      widget.onCompleted(full);
    }
  }

  void _distributePaste(String pasted) {
    final digits = pasted.replaceAll(RegExp(r'[^0-9]'), '');
    for (int i = 0; i < widget.length; i++) {
      _controllers[i].text = i < digits.length ? digits[i] : '';
    }
    final focusIndex = (digits.length - 1).clamp(0, widget.length - 1);
    _focusNodes[focusIndex].requestFocus();

    final full = _currentValue;
    widget.onChanged(full);
    if (full.length == widget.length) {
      widget.onCompleted(full);
    }
  }

  void _onKeyEvent(int index, KeyEvent event) {
    if (event is KeyDownEvent &&
        event.logicalKey == LogicalKeyboardKey.backspace) {
      if (_controllers[index].text.isEmpty && index > 0) {
        _controllers[index - 1].clear();
        _focusNodes[index - 1].requestFocus();
        final full = _currentValue;
        widget.onChanged(full);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      mainAxisSize: MainAxisSize.min,
      children: List.generate(widget.length, (index) {
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 3),
          child: _OtpCell(
            controller: _controllers[index],
            focusNode: _focusNodes[index],
            hasError: widget.hasError,
            enabled: widget.enabled,
            onChanged: (value) => _onCellChanged(index, value),
            onKeyEvent: (event) => _onKeyEvent(index, event),
          ),
        );
      }),
    );
  }
}

class _OtpCell extends StatefulWidget {
  const _OtpCell({
    required this.controller,
    required this.focusNode,
    required this.hasError,
    required this.enabled,
    required this.onChanged,
    required this.onKeyEvent,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool hasError;
  final bool enabled;
  final ValueChanged<String> onChanged;
  final ValueChanged<KeyEvent> onKeyEvent;

  @override
  State<_OtpCell> createState() => _OtpCellState();
}

class _OtpCellState extends State<_OtpCell> {
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    widget.focusNode.addListener(_onFocusChange);
  }

  @override
  void dispose() {
    widget.focusNode.removeListener(_onFocusChange);
    super.dispose();
  }

  void _onFocusChange() {
    setState(() => _isFocused = widget.focusNode.hasFocus);
  }

  @override
  Widget build(BuildContext context) {
    final hasValue = widget.controller.text.isNotEmpty;
    final Color borderColor;

    if (widget.hasError) {
      borderColor = AppColors.error;
    } else if (_isFocused || hasValue) {
      borderColor = AppColors.primary;
    } else {
      borderColor = AppColors.inputBorder;
    }

    final Color backgroundColor;
    if (hasValue && !widget.hasError) {
      backgroundColor = AppColors.primary.withValues(alpha: 0.08);
    } else {
      backgroundColor = Colors.white;
    }

    return KeyboardListener(
      focusNode: FocusNode(skipTraversal: true),
      onKeyEvent: widget.onKeyEvent,
      child: SizedBox(
        width: 40,
        height: 52,
        child: Container(
          decoration: BoxDecoration(
            color: backgroundColor,
            border: Border(
              bottom: BorderSide(
                color: borderColor,
                width: _isFocused || widget.hasError ? 2 : 1.5,
              ),
            ),
          ),
          child: TextField(
            controller: widget.controller,
            focusNode: widget.focusNode,
            enabled: widget.enabled,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: widget.hasError ? AppColors.error : AppColors.textPrimary,
            ),
            keyboardType: TextInputType.number,
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(1),
            ],
            decoration: const InputDecoration(
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              filled: false,
              contentPadding: EdgeInsets.zero,
            ),
            onChanged: widget.onChanged,
          ),
        ),
      ),
    );
  }
}
