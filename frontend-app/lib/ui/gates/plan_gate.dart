import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/subscription/presentation/providers/subscription_provider.dart';

/// Centralised premium-gate wrapper for any tappable row or action.
///
/// Wraps [child] with a tap handler. When the current user is premium the
/// [onTap] callback fires as-is. For free users the tap instead routes to
/// `/subscription` (the [PlanScreen]) so the user can upgrade. This is the
/// only place where `isPremium` is checked for gating purposes — never
/// duplicate the check across screens (see Prompt 21).
class PlanGate extends ConsumerWidget {
  const PlanGate({
    super.key,
    required this.child,
    required this.onTap,
    this.gated = true,
  });

  /// The widget that receives the tap gesture.
  final Widget child;

  /// Tap callback invoked only when the user is premium (or gating is off).
  final VoidCallback onTap;

  /// When `false` the gate always forwards the tap to [onTap] regardless
  /// of tier (used for non-premium actions rendered through the same row
  /// component). Default `true`.
  final bool gated;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bool isPremium =
        ref.watch(subscriptionNotifierProvider).valueOrNull?.isPremium ?? false;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () {
        if (!gated || isPremium) {
          onTap();
        } else {
          context.push('/subscription');
        }
      },
      child: child,
    );
  }
}
