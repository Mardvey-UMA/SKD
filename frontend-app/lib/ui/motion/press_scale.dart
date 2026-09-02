import 'package:flutter/widgets.dart';

/// Shared press-down micro-interaction wrapper.
///
/// Mirrors the `.tab-anim` class in `design/reference/mockup/radar-web.html`:
///
/// ```css
/// .tab-anim { transition: transform 180ms ease; }
/// .tab-anim:active { transform: scale(0.92); }
/// ```
///
/// Wraps any tappable child in a [GestureDetector] that tracks the
/// `onTapDown` / `onTapUp` / `onTapCancel` lifecycle and drives an
/// [AnimatedScale] between `1.0` and [scale] (default `0.92`) over
/// [duration] (default 180 ms) with [Curves.easeOut].
///
/// Taps are opaque ([HitTestBehavior.opaque]) so that the wrapped region
/// reliably consumes the pointer — a reaction button inside a card, for
/// instance, does not leak through to the card's own `onOpen`.
///
/// Use everywhere the mockup applies `.tab-anim`:
///   * `BottomNav` tabs
///   * `SideNav` tabs
///   * `ReactionBar` buttons
///   * Pill CTAs («Читать →», «Читать далее →», «Новое пространство»,
///     «Продолжить», …)
class PressScale extends StatefulWidget {
  const PressScale({
    super.key,
    required this.child,
    required this.onTap,
    this.scale = 0.92,
    this.duration = const Duration(milliseconds: 180),
  });

  /// Scale applied while the pointer is pressed.
  final double scale;

  /// Duration of the scale animation in both directions.
  final Duration duration;

  /// The widget rendered inside the animated scale.
  final Widget child;

  /// Fired on tap-up (after a complete tap-down → tap-up sequence). Cancelled
  /// gestures (drag-out, scroll) do NOT invoke this callback.
  final VoidCallback onTap;

  @override
  State<PressScale> createState() => _PressScaleState();
}

class _PressScaleState extends State<PressScale> {
  bool _pressed = false;

  void _setPressed(bool value) {
    if (_pressed == value) return;
    setState(() => _pressed = value);
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => _setPressed(true),
      onTapUp: (_) => _setPressed(false),
      onTapCancel: () => _setPressed(false),
      onTap: widget.onTap,
      child: AnimatedScale(
        scale: _pressed ? widget.scale : 1.0,
        duration: widget.duration,
        curve: Curves.easeOut,
        child: widget.child,
      ),
    );
  }
}
