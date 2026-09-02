import 'package:flutter/widgets.dart';

import 'breakpoint.dart';
import 'context_ext.dart';

/// Breakpoint-keyed value with graceful fallback.
///
/// - [tablet] falls back to [mobile].
/// - [desktop] falls back to [tablet], then to [mobile].
///
/// Example:
/// ```dart
/// const padding = ResponsiveValue<double>(mobile: 16, desktop: 32);
/// final value = padding.resolve(context); // 16 on mobile/tablet, 32 on desktop
/// ```
@immutable
class ResponsiveValue<T> {
  const ResponsiveValue({
    required this.mobile,
    this.tablet,
    this.desktop,
  });

  final T mobile;
  final T? tablet;
  final T? desktop;

  /// Resolves the value for the current [BuildContext]'s [Breakpoint].
  T resolve(BuildContext context) => resolveFor(context.breakpoint);

  /// Resolves the value for an explicit [Breakpoint].
  T resolveFor(Breakpoint breakpoint) {
    switch (breakpoint) {
      case Breakpoint.mobile:
        return mobile;
      case Breakpoint.tablet:
        return tablet ?? mobile;
      case Breakpoint.desktop:
        return desktop ?? tablet ?? mobile;
    }
  }
}
