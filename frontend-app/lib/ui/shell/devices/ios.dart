import 'package:flutter/widgets.dart';

/// iPhone-style device bezel (iOS 17/26 era) ported from
/// `design/reference/mockup/ios-frame.jsx` § IOSDevice / IOSStatusBar.
///
/// Primitives only — no Apple trade-dress assets. Dimensions are fixed
/// to the JSX reference (402×874, radius 48, dynamic-island 126×37,
/// home-indicator 139×5).
class IOSDevice extends StatelessWidget {
  const IOSDevice({
    super.key,
    required this.child,
    this.width = 402,
    this.height = 874,
    this.time = '9:41',
  });

  final Widget child;
  final double width;
  final double height;
  final String time;

  static const Color _shell = Color(0xFFF2F2F7);
  static const Color _ink = Color(0xFF000000);

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: _shell,
        borderRadius: BorderRadius.circular(48),
        boxShadow: const <BoxShadow>[
          BoxShadow(
            offset: Offset(0, 40),
            blurRadius: 80,
            color: Color.fromRGBO(0, 0, 0, 0.18),
          ),
          BoxShadow(
            blurRadius: 0,
            spreadRadius: 1,
            color: Color.fromRGBO(0, 0, 0, 0.12),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: child),
          // Status bar (absolute top).
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: _IOSStatusBar(time: time),
          ),
          // Dynamic island (pill) — centred, 11-px from top.
          Positioned(
            top: 11,
            left: 0,
            right: 0,
            child: Center(
              child: Container(
                width: 126,
                height: 37,
                decoration: BoxDecoration(
                  color: _ink,
                  borderRadius: BorderRadius.circular(24),
                ),
              ),
            ),
          ),
          // Home indicator (always on top).
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            height: 34,
            child: IgnorePointer(
              child: Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Container(
                    width: 139,
                    height: 5,
                    decoration: BoxDecoration(
                      color: const Color.fromRGBO(0, 0, 0, 0.25),
                      borderRadius: BorderRadius.circular(100),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _IOSStatusBar extends StatelessWidget {
  const _IOSStatusBar({required this.time});

  final String time;

  static const Color _c = Color(0xFF000000);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 21, 24, 19),
      child: SizedBox(
        height: 22,
        child: Row(
          children: <Widget>[
            // Left cluster — time (SF-style weight 590 ≈ w600).
            Expanded(
              child: Center(
                child: Text(
                  time,
                  style: const TextStyle(
                    fontSize: 17,
                    height: 22 / 17,
                    fontWeight: FontWeight.w600,
                    color: _c,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 126), // Dynamic-island reservation.
            // Right cluster — signal / wifi / battery.
            Expanded(
              child: Center(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: const <Widget>[
                    _SignalBars(color: _c),
                    SizedBox(width: 7),
                    _WifiGlyph(color: _c),
                    SizedBox(width: 7),
                    _BatteryGlyph(color: _c),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SignalBars extends StatelessWidget {
  const _SignalBars({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 19,
      height: 12,
      child: CustomPaint(
        painter: _SignalBarsPainter(color),
      ),
    );
  }
}

class _SignalBarsPainter extends CustomPainter {
  _SignalBarsPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint paint = Paint()..color = color;
    void bar(double x, double y, double w, double h) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, y, w, h),
          const Radius.circular(0.7),
        ),
        paint,
      );
    }

    bar(0, 7.5, 3.2, 4.5);
    bar(4.8, 5, 3.2, 7);
    bar(9.6, 2.5, 3.2, 9.5);
    bar(14.4, 0, 3.2, 12);
  }

  @override
  bool shouldRepaint(covariant _SignalBarsPainter old) => old.color != color;
}

class _WifiGlyph extends StatelessWidget {
  const _WifiGlyph({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 17,
      height: 12,
      child: CustomPaint(
        painter: _WifiPainter(color),
      ),
    );
  }
}

class _WifiPainter extends CustomPainter {
  _WifiPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint paint = Paint()..color = color;
    // Outer arc — approximated as two stacked chevrons (rectangles rotated)
    // keep simple: dots + rounded bars.
    final Path outer = Path()
      ..moveTo(8.5, 3.2)
      ..relativeLineTo(4.6, 0)
      ..arcToPoint(const Offset(15.5, 4.5), radius: const Radius.circular(1))
      ..lineTo(14.4, 5.6)
      ..arcToPoint(const Offset(8.5, 3.2), radius: const Radius.circular(6))
      ..close();
    canvas.drawPath(outer, paint);

    final Path mid = Path()
      ..moveTo(8.5, 6.8)
      ..arcToPoint(const Offset(13.1, 7.1), radius: const Radius.circular(4))
      ..lineTo(12, 8.2)
      ..arcToPoint(const Offset(8.5, 6.8), radius: const Radius.circular(4))
      ..close();
    canvas.drawPath(mid, paint);

    canvas.drawCircle(const Offset(8.5, 10.5), 1.5, paint);
  }

  @override
  bool shouldRepaint(covariant _WifiPainter old) => old.color != color;
}

class _BatteryGlyph extends StatelessWidget {
  const _BatteryGlyph({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 27,
      height: 13,
      child: CustomPaint(
        painter: _BatteryPainter(color),
      ),
    );
  }
}

class _BatteryPainter extends CustomPainter {
  _BatteryPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint body = Paint()..color = color;
    final Paint stroke = Paint()
      ..color = color.withValues(alpha: 0.35)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;

    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(0.5, 0.5, 23, 12),
        const Radius.circular(3.5),
      ),
      stroke,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(2, 2, 20, 9),
        const Radius.circular(2),
      ),
      body,
    );
    // Nib.
    final Paint nib = Paint()..color = color.withValues(alpha: 0.4);
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(25, 4.5, 1.5, 4),
        const Radius.circular(0.7),
      ),
      nib,
    );
  }

  @override
  bool shouldRepaint(covariant _BatteryPainter old) => old.color != color;
}
