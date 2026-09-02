import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';

/// Bottom-pinned toast for transient feedback — «Источник скрыт», «Добавлено
/// в …» etc. Lives inside `ResponsiveShell`, NOT the root `Scaffold`.
///
/// Mirrors the inline `toast` element in `radar-web.html`. Positioned
/// 90 px above the parent bottom, slides + fades in over 220 ms and
/// auto-dismisses after [duration]. An optional [undoLabel] + [onUndo]
/// render a lime action button on the right.
///
/// The widget is self-dismissing: once [duration] elapses it calls
/// [onDismiss]. Parents own the lifecycle and simply render the toast
/// when non-null, removing it from the tree on dismiss.
class Toast extends StatefulWidget {
  const Toast({
    super.key,
    required this.text,
    required this.duration,
    required this.onDismiss,
    this.undoLabel,
    this.onUndo,
  });

  /// Message body.
  final String text;

  /// How long the toast stays visible before auto-dismissing. Caller passes
  /// e.g. `Duration(milliseconds: 2600)` for normal messages or `3400` for
  /// messages with an undo action.
  final Duration duration;

  /// Called when the auto-dismiss timer elapses OR the user taps the undo
  /// action. Parents remove the toast from the tree on this callback.
  final VoidCallback onDismiss;

  /// Optional undo-button label. When both [undoLabel] and [onUndo] are
  /// non-null, a lime-coloured tap target renders on the right.
  final String? undoLabel;

  /// Called when the undo button is tapped. The widget will also call
  /// [onDismiss] right after so parents can uniformly tear it down.
  final VoidCallback? onUndo;

  @override
  State<Toast> createState() => _ToastState();
}

class _ToastState extends State<Toast> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _opacity;
  late final Animation<Offset> _slide;
  Timer? _dismissTimer;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 220),
    )..forward();
    _opacity = CurvedAnimation(parent: _controller, curve: Curves.easeOut);
    _slide = Tween<Offset>(
      begin: const Offset(0, 0.3),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));
    _dismissTimer = Timer(widget.duration, widget.onDismiss);
  }

  @override
  void dispose() {
    _dismissTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _handleUndo() {
    widget.onUndo?.call();
    widget.onDismiss();
  }

  @override
  Widget build(BuildContext context) {
    final bool hasUndo = widget.undoLabel != null && widget.onUndo != null;
    return Positioned(
      left: 14,
      right: 14,
      bottom: 90,
      child: IgnorePointer(
        ignoring: false,
        child: FadeTransition(
          opacity: _opacity,
          child: SlideTransition(
            position: _slide,
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 12,
                  ),
                  decoration: BoxDecoration(
                    color: NFColors.ink,
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: const <BoxShadow>[
                      BoxShadow(
                        color: Color.fromRGBO(0, 0, 0, 0.4),
                        blurRadius: 40,
                        offset: Offset(0, 20),
                      ),
                    ],
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: <Widget>[
                      Expanded(
                        child: Text(
                          widget.text,
                          style: const TextStyle(
                            fontFamily: 'Nunito',
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                            color: Color(0xFFFFFFFF),
                          ),
                        ),
                      ),
                      if (hasUndo) ...<Widget>[
                        const SizedBox(width: 10),
                        GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onTap: _handleUndo,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            child: Text(
                              widget.undoLabel!,
                              style: const TextStyle(
                                fontFamily: 'Nunito',
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                                color: NFColors.lime,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ],
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
