import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Diagonal stripe-pattern tone palette. Mirrors the `hues` map in the
/// `Stripe` JSX component (`design/reference/mockup/tokens.jsx`). Each tone
/// resolves to a `(primary, secondary)` colour pair rendered as a 135°
/// 14/14-px repeating gradient.
enum StripeTone {
  ink(Color(0xFF1C1D1B), Color(0xFF2A2B28)),
  accent(Color(0xFF3B2BFF), Color(0xFF2218C7)),
  lime(Color(0xFFCCFF33), Color(0xFFB7E620)),
  light(Color(0xFFE8E9E3), Color(0xFFDADBD4)),
  warn(Color(0xFFFF5A1F), Color(0xFFE34B13)),
  violet(Color(0xFF7A4BFF), Color(0xFF5A2BDB)),
  teal(Color(0xFF1EC6B6), Color(0xFF0F9E90)),
  rose(Color(0xFFFF7A8A), Color(0xFFE0566A));

  const StripeTone(this.primary, this.secondary);

  final Color primary;
  final Color secondary;
}

/// Unified image-placeholder used in cards + detail screens. Draws the
/// 135° stripe pattern + radial highlight overlay + optional mono label
/// and seed `#NNN` exactly as the web mockup's `<Stripe/>` component.
class StripePlaceholder extends StatelessWidget {
  const StripePlaceholder({
    super.key,
    this.height = 200,
    this.width,
    this.tone = StripeTone.ink,
    this.label = 'ФОТО',
    this.seed = 0,
    this.radius = 16,
    this.child,
  });

  final double height;
  final double? width;
  final StripeTone tone;
  final String? label;
  final int seed;
  final double radius;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: SizedBox(
        width: width ?? double.infinity,
        height: height,
        child: CustomPaint(
          painter: _StripePainter(tone: tone, label: label, seed: seed),
          child: child,
        ),
      ),
    );
  }
}

class _StripePainter extends CustomPainter {
  _StripePainter({
    required this.tone,
    required this.label,
    required this.seed,
  });

  final StripeTone tone;
  final String? label;
  final int seed;

  static const double _tile = 14;
  static const double _angleRad = 135 * math.pi / 180;

  @override
  void paint(Canvas canvas, Size size) {
    // Opaque base fill so the stripe pattern never leaves background
    // pixels showing through under anti-aliased tile edges.
    final Paint base = Paint()..color = tone.primary;
    canvas.drawRect(Offset.zero & size, base);

    canvas.save();
    final Rect rect = Offset.zero & size;
    canvas.clipRect(rect);

    final Offset center = rect.center;
    canvas.translate(center.dx, center.dy);
    canvas.rotate(_angleRad);

    // Diagonal diameter guarantees the rotated tile band covers the full
    // clipped rect at any aspect ratio.
    final double diag = math.sqrt(size.width * size.width + size.height * size.height);
    final double half = diag / 2 + _tile * 2;
    final Paint secondary = Paint()..color = tone.secondary;
    double y = -half;
    bool flip = false;
    while (y < half) {
      if (flip) {
        canvas.drawRect(Rect.fromLTWH(-half, y, diag + _tile * 4, _tile), secondary);
      }
      y += _tile;
      flip = !flip;
    }
    canvas.restore();

    _paintHighlight(canvas, size);
    _paintLabel(canvas, size);
    _paintSeed(canvas, size);
  }

  void _paintHighlight(Canvas canvas, Size size) {
    final Rect rect = Offset.zero & size;
    // `radial-gradient(120% 80% at 10% 0%, rgba(255,255,255,0.14), rgba(255,255,255,0) 60%)`
    final Paint paint = Paint()
      ..shader = RadialGradient(
        center: const Alignment(-0.8, -1),
        radius: 1.2,
        colors: const <Color>[Color(0x24FFFFFF), Color(0x00FFFFFF)],
        stops: const <double>[0.0, 0.6],
      ).createShader(rect);
    canvas.drawRect(rect, paint);
  }

  void _paintLabel(Canvas canvas, Size size) {
    final String? text = label;
    if (text == null || text.isEmpty) return;
    final TextPainter painter = TextPainter(
      text: TextSpan(
        text: text.toUpperCase(),
        style: const TextStyle(
          fontFamily: 'Nunito',
          fontSize: 9,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.2,
          color: Color(0xD9FFFFFF), // rgba(255,255,255,0.85)
          height: 1,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();

    const double padH = 7;
    const double padV = 3;
    final Rect box = Rect.fromLTWH(
      10,
      10,
      painter.width + padH * 2,
      painter.height + padV * 2,
    );
    final Paint border = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = const Color(0x59FFFFFF); // rgba(255,255,255,0.35)
    canvas.drawRRect(RRect.fromRectAndRadius(box, const Radius.circular(4)), border);
    painter.paint(canvas, Offset(box.left + padH, box.top + padV));
  }

  void _paintSeed(Canvas canvas, Size size) {
    final String formatted = '#${seed.toString().padLeft(3, '0')}';
    final TextPainter painter = TextPainter(
      text: TextSpan(
        text: formatted,
        style: const TextStyle(
          fontFamily: 'Nunito',
          fontSize: 9,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.2,
          color: Color(0xB3FFFFFF), // rgba(255,255,255,0.7)
          height: 1,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(
      canvas,
      Offset(size.width - painter.width - 10, size.height - painter.height - 10),
    );
  }

  @override
  bool shouldRepaint(covariant _StripePainter old) {
    return old.tone != tone || old.label != label || old.seed != seed;
  }
}
