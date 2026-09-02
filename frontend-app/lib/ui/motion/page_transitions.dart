import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

/// Page transition tokens — mirrors `@keyframes fade` in
/// `design/reference/mockup/radar-web.html`:
/// `from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; }`
/// with `animation: fade 260ms ease`.
class NFMotion {
  const NFMotion._();

  /// Screen enter/exit duration. Matches the 260ms CSS `@keyframes fade`.
  static const Duration pageDuration = Duration(milliseconds: 260);

  /// Hero flight duration. Kept under 320ms — longer feels laggy.
  static const Duration heroDuration = Duration(milliseconds: 320);

  /// Screen enter curve. `ease` in CSS ≈ `Curves.easeOut` in Flutter.
  static const Curve pageCurve = Curves.easeOut;

  /// Hero flight curve.
  static const Curve heroCurve = Curves.easeOutCubic;

  /// Initial vertical offset of the enter animation — 4 px rise.
  static const Offset riseOffset = Offset(0, 0.004);
}

/// Builds the fade+rise enter transition used by every routed screen.
///
/// Combines a [FadeTransition] (0 → 1) with a [SlideTransition] that shifts
/// the page up by 4 px (expressed as a fractional `Offset(0, 0.004)` on a
/// typical mobile height — effectively the same 4 px as the CSS keyframe).
Widget nfFadeRiseTransition(
  BuildContext context,
  Animation<double> animation,
  Animation<double> secondaryAnimation,
  Widget child,
) {
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
      child: child,
    ),
  );
}

/// Low-level [PageRouteBuilder] — useful for non-go_router navigation
/// or for imperative pushes that still need the neo-futurism fade.
class NFFadeRisePageRoute<T> extends PageRouteBuilder<T> {
  NFFadeRisePageRoute({required WidgetBuilder builder, super.settings})
      : super(
          transitionDuration: NFMotion.pageDuration,
          reverseTransitionDuration: NFMotion.pageDuration,
          pageBuilder: (context, _, _) => builder(context),
          transitionsBuilder: nfFadeRiseTransition,
        );
}

/// Builds a [CustomTransitionPage] for go_router with the shared fade+rise
/// transition. Called from route `pageBuilder` callbacks.
CustomTransitionPage<T> buildFadeRisePage<T>({
  required Widget child,
  LocalKey? key,
  Object? arguments,
  String? restorationId,
  String? name,
}) {
  return CustomTransitionPage<T>(
    key: key,
    arguments: arguments,
    restorationId: restorationId,
    name: name,
    transitionDuration: NFMotion.pageDuration,
    reverseTransitionDuration: NFMotion.pageDuration,
    child: child,
    transitionsBuilder: nfFadeRiseTransition,
  );
}
