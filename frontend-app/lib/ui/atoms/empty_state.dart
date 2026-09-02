import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../theme/colors.dart';
import '../../theme/radii.dart';
import 'nf_icon.dart';

/// Friendly placeholder shown in empty likes/dislikes/bookmarks lists.
///
/// Mirrors `EmptyState` in `design/reference/mockup/cards.jsx`. Two concentric
/// dashed rings rotate in opposite directions (outer 16 s, inner 10 s
/// reverse) around an 86×86 accent disk that floats ±4 px on a 3 s sine,
/// with an [NFIcon] centered inside the disk.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    this.iconName = 'bookmark',
    required this.title,
    this.desc,
    this.accent = EmptyStateAccent.lime,
    this.action,
  });

  final String iconName;
  final String title;
  final String? desc;
  final EmptyStateAccent accent;

  /// Optional primary pill action rendered beneath the description.
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: NFRadii.brLg,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: NFRadii.brLg,
          border: Border.all(color: NFColors.hairline),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(22, 36, 22, 36),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              _Badge(iconName: iconName, accent: accent),
              const SizedBox(height: 16),
              Text(
                title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.3,
                  color: NFColors.ink,
                ),
              ),
              if (desc != null) ...<Widget>[
                const SizedBox(height: 6),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 260),
                  child: Text(
                    desc!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 13.5,
                      height: 1.5,
                      color: NFColors.mute,
                    ),
                  ),
                ),
              ],
              if (action != null) ...<Widget>[
                const SizedBox(height: 18),
                action!,
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Accent colour palette for the central disk.
enum EmptyStateAccent {
  lime(NFColors.lime, NFColors.limeInk),
  accent(NFColors.accent, NFColors.accentInk),
  ink(NFColors.ink, Color(0xFFFFFFFF));

  const EmptyStateAccent(this.bg, this.fg);

  final Color bg;
  final Color fg;
}

class _Badge extends StatefulWidget {
  const _Badge({required this.iconName, required this.accent});

  final String iconName;
  final EmptyStateAccent accent;

  @override
  State<_Badge> createState() => _BadgeState();
}

class _BadgeState extends State<_Badge> with TickerProviderStateMixin {
  late final AnimationController _outer;
  late final AnimationController _inner;
  late final AnimationController _float;

  @override
  void initState() {
    super.initState();
    _outer = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 16),
    )..repeat();
    _inner = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 10),
    )..repeat();
    _float = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 3),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _outer.dispose();
    _inner.dispose();
    _float.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 86,
      height: 86,
      child: Stack(
        children: <Widget>[
          Positioned.fill(
            child: AnimatedBuilder(
              animation: _outer,
              builder: (BuildContext context, _) {
                return CustomPaint(
                  painter: _DashedRingPainter(
                    rotation: _outer.value * 2 * math.pi,
                    inset: 0,
                  ),
                );
              },
            ),
          ),
          Positioned.fill(
            child: AnimatedBuilder(
              animation: _inner,
              builder: (BuildContext context, _) {
                return CustomPaint(
                  painter: _DashedRingPainter(
                    rotation: -_inner.value * 2 * math.pi,
                    inset: 12,
                  ),
                );
              },
            ),
          ),
          Positioned.fill(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: AnimatedBuilder(
                animation: _float,
                builder: (BuildContext context, Widget? child) {
                  final double eased = Curves.easeInOut.transform(_float.value);
                  // Triangle 0 → 1 → 0 (reverse repeat) → offset band ±4 px.
                  final double dy = -4 * eased;
                  return Transform.translate(
                    offset: Offset(0, dy),
                    child: child,
                  );
                },
                child: _AccentDisk(
                  accent: widget.accent,
                  iconName: widget.iconName,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AccentDisk extends StatelessWidget {
  const _AccentDisk({required this.accent, required this.iconName});

  final EmptyStateAccent accent;
  final String iconName;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: accent.bg,
        shape: BoxShape.circle,
      ),
      child: Center(
        child: NFIcon(iconName, size: 22, color: accent.fg),
      ),
    );
  }
}

class _DashedRingPainter extends CustomPainter {
  _DashedRingPainter({required this.rotation, required this.inset});

  final double rotation;
  final double inset;

  static const double _stroke = 1.5;
  static const double _dashLen = 4.0;
  static const double _gapLen = 4.0;

  @override
  void paint(Canvas canvas, Size size) {
    final Rect full = Offset.zero & size;
    final Rect ringBounds = full.deflate(inset);
    if (ringBounds.isEmpty) return;

    final Offset center = ringBounds.center;
    final double radius = ringBounds.shortestSide / 2 - _stroke / 2;
    if (radius <= 0) return;

    final Paint paint = Paint()
      ..color = NFColors.hairline
      ..strokeWidth = _stroke
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.butt
      ..isAntiAlias = true;

    final double circumference = 2 * math.pi * radius;
    final double segment = _dashLen + _gapLen;
    final int dashCount = (circumference / segment).floor();
    // Distribute the remainder so we end exactly at the start angle.
    final double stepRad = (2 * math.pi) / dashCount;
    final double dashRad = stepRad * (_dashLen / segment);

    final Rect oval = Rect.fromCircle(center: center, radius: radius);
    for (int i = 0; i < dashCount; i++) {
      final double start = rotation + i * stepRad;
      canvas.drawArc(oval, start, dashRad, false, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _DashedRingPainter old) {
    return old.rotation != rotation || old.inset != inset;
  }
}
