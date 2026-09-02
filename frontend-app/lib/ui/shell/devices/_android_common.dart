import 'package:flutter/widgets.dart';

/// Shared Android status-bar helper for [PixelDevice] and [GalaxyDevice].
///
/// Ported from `design/reference/mockup/device-frame.jsx` § AndroidStatus —
/// 32-px row with time on the left and signal / wifi / battery glyphs on
/// the right. Primitives only, no trade-dress assets.
class AndroidStatus extends StatelessWidget {
  const AndroidStatus({
    super.key,
    this.time = '9:41',
    this.dark = false,
  });

  final String time;
  final bool dark;

  static const Color _light = Color(0xFF0E0F0D);
  static const Color _darkC = Color(0xFFFFFFFF);

  @override
  Widget build(BuildContext context) {
    final Color c = dark ? _darkC : _light;
    return SizedBox(
      height: 32,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 22),
        child: Row(
          children: <Widget>[
            Text(
              time,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                letterSpacing: 0.1,
                color: c,
                height: 1.0,
              ),
            ),
            const Spacer(),
            _AndroidSignalBars(color: c),
            const SizedBox(width: 5),
            _AndroidWifiGlyph(color: c),
            const SizedBox(width: 5),
            _AndroidBatteryGlyph(color: c),
          ],
        ),
      ),
    );
  }
}

/// Shared Android gesture-bar helper for [PixelDevice] and [GalaxyDevice].
///
/// Ported from `design/reference/mockup/device-frame.jsx` § AndroidGestureBar.
/// 22-px-tall row with a centred 120×4 pill 7-px from the bottom edge.
class AndroidGestureBar extends StatelessWidget {
  const AndroidGestureBar({super.key, this.dark = false});

  final bool dark;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: SizedBox(
        height: 22,
        child: Align(
          alignment: Alignment.bottomCenter,
          child: Padding(
            padding: const EdgeInsets.only(bottom: 7),
            child: Container(
              width: 120,
              height: 4,
              decoration: BoxDecoration(
                color: dark
                    ? const Color.fromRGBO(255, 255, 255, 0.75)
                    : const Color.fromRGBO(14, 15, 13, 0.55),
                borderRadius: BorderRadius.circular(100),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AndroidSignalBars extends StatelessWidget {
  const _AndroidSignalBars({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 16,
      height: 12,
      child: CustomPaint(painter: _SignalPainter(color)),
    );
  }
}

class _SignalPainter extends CustomPainter {
  _SignalPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint paint = Paint()..color = color;
    void bar(double x, double y, double w, double h) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, y, w, h),
          const Radius.circular(0.6),
        ),
        paint,
      );
    }

    bar(0, 8, 3, 4);
    bar(4.5, 5, 3, 7);
    bar(9, 2, 3, 10);
  }

  @override
  bool shouldRepaint(covariant _SignalPainter old) => old.color != color;
}

class _AndroidWifiGlyph extends StatelessWidget {
  const _AndroidWifiGlyph({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 14,
      height: 12,
      child: CustomPaint(painter: _WifiPainter(color)),
    );
  }
}

class _WifiPainter extends CustomPainter {
  _WifiPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint paint = Paint()..color = color;
    // Outer ring (approximation of two stacked chevrons from JSX).
    final Path outer = Path()
      ..moveTo(7, 3)
      ..arcToPoint(const Offset(12.1, 5.1), radius: const Radius.circular(5.5))
      ..lineTo(13, 4.2)
      ..arcToPoint(const Offset(1, 4.2), radius: const Radius.circular(6.5))
      ..lineTo(1.9, 5.1)
      ..arcToPoint(const Offset(7, 3), radius: const Radius.circular(5.5))
      ..close();
    canvas.drawPath(outer, paint);
    // Inner.
    final Path inner = Path()
      ..moveTo(7, 6)
      ..arcToPoint(const Offset(10, 7.2), radius: const Radius.circular(3.2))
      ..lineTo(10.9, 6.3)
      ..arcToPoint(const Offset(3.1, 6.3), radius: const Radius.circular(4))
      ..lineTo(4, 7.2)
      ..arcToPoint(const Offset(7, 6), radius: const Radius.circular(3.2))
      ..close();
    canvas.drawPath(inner, paint);
    // Dot.
    canvas.drawCircle(const Offset(7, 9.5), 1.3, paint);
  }

  @override
  bool shouldRepaint(covariant _WifiPainter old) => old.color != color;
}

class _AndroidBatteryGlyph extends StatelessWidget {
  const _AndroidBatteryGlyph({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 22,
      height: 11,
      child: CustomPaint(painter: _BatteryPainter(color)),
    );
  }
}

class _BatteryPainter extends CustomPainter {
  _BatteryPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint stroke = Paint()
      ..color = color.withValues(alpha: 0.45)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    final Paint fill = Paint()..color = color;

    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(0.5, 0.5, 19, 10),
        const Radius.circular(2.5),
      ),
      stroke,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(2, 2, 16, 7),
        const Radius.circular(1.4),
      ),
      fill,
    );
    final Paint nib = Paint()..color = color.withValues(alpha: 0.45);
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(20, 3.5, 1.5, 4),
        const Radius.circular(0.7),
      ),
      nib,
    );
  }

  @override
  bool shouldRepaint(covariant _BatteryPainter old) => old.color != color;
}
