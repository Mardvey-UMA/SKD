import 'dart:math' as math;

import 'package:flutter/rendering.dart';

/// CustomPainter reproducing the CSS
/// `repeating-linear-gradient(135deg, transparent 0 Xpx, rgba(14,15,13,α) Xpx Ypx)`
/// pattern used across the sponsored-content surfaces.
///
/// Strokes are perpendicular to a 135° baseline — meaning the visible
/// diagonal lines run from top-left to bottom-right (`+x, +y`). [period] is
/// the full cycle (transparent gap + ink stroke) in logical pixels.
/// [strokeWidth] is the opaque portion; [color] is the line color.
///
/// Default values mirror the `card` header band from the JSX mockup (period
/// 14, stroke 2, ink 7%). The `banner` icon tile uses period 9 / stroke 1 /
/// ink 8% — pass explicit params for that.
///
/// Reusable — extracted for P18 (collection hatched tiles).
class HatchedPainter extends CustomPainter {
  const HatchedPainter({
    this.period = 14,
    this.strokeWidth = 2,
    this.color = const Color(0x120E0F0D),
  });

  /// Length of one full repeat cycle (transparent gap + visible stroke).
  final double period;

  /// Width of the visible diagonal stroke inside one cycle.
  final double strokeWidth;

  /// Stroke color, usually a low-alpha ink tone.
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty || period <= 0 || strokeWidth <= 0) {
      return;
    }

    final Paint paint = Paint()
      ..color = color
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke
      ..isAntiAlias = true;

    canvas.save();
    canvas.clipRect(Offset.zero & size);

    // 135° in CSS lands at top-left → bottom-right diagonals. Step vector
    // perpendicular to the stroke direction: (period / sqrt2, period / sqrt2).
    final double step = period * math.sqrt2;
    final double diagonal = size.width + size.height;

    // Start far enough left so strokes fill the top-right corner too.
    for (double offset = -diagonal; offset <= diagonal; offset += step) {
      final Offset p1 = Offset(offset, 0);
      final Offset p2 = Offset(offset + size.height, size.height);
      canvas.drawLine(p1, p2, paint);
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant HatchedPainter oldDelegate) {
    return oldDelegate.period != period ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.color != color;
  }
}
