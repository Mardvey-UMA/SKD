import 'package:flutter/material.dart';
import 'package:visibility_detector/visibility_detector.dart';

import '../../theme/colors.dart';
import '../../theme/radii.dart';
import 'nf_text.dart';

/// Three placeholder cards rendered with a shared 1400 ms shimmer sweep plus
/// a header lime pulse-dot and status mono-caption.
///
/// Mirrors `FeedSkeleton` in `design/reference/mockup/cards.jsx`. A single
/// [AnimationController] drives the shimmer sweep across all three cards
/// through one [ShaderMask] — per spec, controllers MUST NOT be created per
/// skeleton card.
///
/// Wrapped in a [VisibilityDetector] + [TickerMode] so animations pause when
/// the widget leaves the viewport, avoiding needless CPU while scrolled off.
class FeedSkeleton extends StatefulWidget {
  const FeedSkeleton({super.key});

  @override
  State<FeedSkeleton> createState() => _FeedSkeletonState();
}

class _FeedSkeletonState extends State<FeedSkeleton> {
  bool _visible = true;

  void _handleVisibility(VisibilityInfo info) {
    final bool next = info.visibleFraction > 0;
    if (next != _visible && mounted) {
      setState(() => _visible = next);
    }
  }

  @override
  Widget build(BuildContext context) {
    return VisibilityDetector(
      key: const Key('feed-skeleton'),
      onVisibilityChanged: _handleVisibility,
      child: TickerMode(
        enabled: _visible,
        child: const _FeedSkeletonBody(),
      ),
    );
  }
}

class _FeedSkeletonBody extends StatefulWidget {
  const _FeedSkeletonBody();

  @override
  State<_FeedSkeletonBody> createState() => _FeedSkeletonBodyState();
}

class _FeedSkeletonBodyState extends State<_FeedSkeletonBody>
    with TickerProviderStateMixin {
  late final AnimationController _shimmer;
  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    _shimmer = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1300),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _shimmer.dispose();
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _shimmer,
      builder: (BuildContext context, Widget? child) {
        return ShaderMask(
          shaderCallback: _buildShimmerShader,
          blendMode: BlendMode.srcATop,
          child: child,
        );
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          _Header(pulse: _pulse),
          const SizedBox(height: 14),
          const _SkeletonCard(),
          const SizedBox(height: 14),
          const _SkeletonCard(),
          const SizedBox(height: 14),
          const _SkeletonCard(),
        ],
      ),
    );
  }

  Shader _buildShimmerShader(Rect bounds) {
    // Sweep a highlight band from −400 → 400 px horizontally across the
    // animation's 1400 ms cycle. Gradient stops mimic the CSS
    // `linear-gradient(90deg, surface2, chipBg, surface2)` pattern used in
    // the mockup's `.rdr-shimmer` keyframes.
    final double offset = -400 + 800 * _shimmer.value;
    return const LinearGradient(
      begin: Alignment.centerLeft,
      end: Alignment.centerRight,
      colors: <Color>[
        NFColors.surface2,
        NFColors.chipBg,
        NFColors.surface2,
      ],
      stops: <double>[0.0, 0.5, 1.0],
      tileMode: TileMode.clamp,
    ).createShader(Rect.fromLTWH(
      bounds.left + offset,
      bounds.top,
      800,
      bounds.height,
    ));
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.pulse});

  final Animation<double> pulse;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 6),
      child: Column(
        children: <Widget>[
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              _PulseDot(pulse: pulse),
              const SizedBox(width: 8),
              const NFText.mono(
                'ВАША ЛЕНТА ФОРМИРУЕТСЯ...',
                color: NFColors.ink2,
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text(
            'Подбираем свежие материалы по вашим интересам',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 13,
              color: NFColors.mute,
            ),
          ),
        ],
      ),
    );
  }
}

class _PulseDot extends StatelessWidget {
  const _PulseDot({required this.pulse});

  final Animation<double> pulse;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: pulse,
      builder: (BuildContext context, Widget? child) {
        // `repeat(reverse: true)` gives 0→1→0 triangle; eased gives the
        // ease-in-out 1 → 1.6 → 1 scale and 1 → 0.5 → 1 opacity match from
        // the JSX `rdrPulse` keyframes.
        final double eased = Curves.easeInOut.transform(pulse.value);
        final double scale = 1.0 + eased * 0.6;
        final double opacity = 1.0 - eased * 0.5;
        return Opacity(
          opacity: opacity,
          child: Transform.scale(
            scale: scale,
            child: child,
          ),
        );
      },
      child: Container(
        width: 10,
        height: 10,
        decoration: const BoxDecoration(
          color: NFColors.lime,
          shape: BoxShape.circle,
        ),
      ),
    );
  }
}

class _SkeletonCard extends StatelessWidget {
  const _SkeletonCard();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.hairline),
      ),
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const _Block(height: 180, radius: NFRadii.radius),
            const Padding(
              padding: EdgeInsets.fromLTRB(10, 14, 10, 6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  _Block(height: 14, widthFactor: 0.45, radius: 7),
                  SizedBox(height: 10),
                  _Block(height: 20, widthFactor: 0.85, radius: 8),
                  SizedBox(height: 8),
                  _Block(height: 14, widthFactor: 0.70, radius: 7),
                  SizedBox(height: 14),
                  Row(
                    children: <Widget>[
                      _Block(height: 36, width: 36, radius: 999),
                      SizedBox(width: 8),
                      _Block(height: 36, width: 36, radius: 999),
                      SizedBox(width: 8),
                      _Block(height: 36, width: 36, radius: 999),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Block extends StatelessWidget {
  const _Block({
    required this.height,
    this.width,
    this.widthFactor,
    required this.radius,
  });

  final double height;
  final double? width;
  final double? widthFactor;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final Widget box = SizedBox(
      height: height,
      width: width,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: NFColors.surface2,
          borderRadius: BorderRadius.circular(radius),
        ),
      ),
    );
    if (width != null) return box;
    if (widthFactor != null) {
      return Align(
        alignment: Alignment.centerLeft,
        child: FractionallySizedBox(
          widthFactor: widthFactor,
          child: box,
        ),
      );
    }
    return box;
  }
}
