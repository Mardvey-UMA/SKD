import 'package:flutter/widgets.dart';

import '../ui/motion/page_transitions.dart';

/// Fades between two child widgets with the same 260ms fade+rise motion
/// used for full page routes. Wrap content that swaps based on some state
/// key (breakpoint, tab, loaded state) to keep transitions consistent with
/// navigation.
///
/// Children are keyed — provide distinct [ValueKey]s (or rely on runtime
/// type differing) so [AnimatedSwitcher] detects the swap.
class ScreenFade extends StatelessWidget {
  const ScreenFade({
    super.key,
    required this.child,
    this.duration = NFMotion.pageDuration,
  });

  final Widget child;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: duration,
      switchInCurve: NFMotion.pageCurve,
      switchOutCurve: NFMotion.pageCurve,
      transitionBuilder: (Widget c, Animation<double> animation) {
        final CurvedAnimation curved = CurvedAnimation(
          parent: animation,
          curve: NFMotion.pageCurve,
        );
        return FadeTransition(
          opacity: curved,
          child: SlideTransition(
            position: Tween<Offset>(
              begin: NFMotion.riseOffset,
              end: Offset.zero,
            ).animate(curved),
            child: c,
          ),
        );
      },
      child: child,
    );
  }
}
