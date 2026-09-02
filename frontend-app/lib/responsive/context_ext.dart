import 'package:flutter/widgets.dart';

import 'breakpoint.dart';

/// Window-size based [Breakpoint] helpers on [BuildContext].
///
/// Reads `MediaQuery.sizeOf(this).width` — equivalent to the web
/// `useLayout()` hook. Prefer [ResponsiveBuilder] when a nested constraint
/// (e.g. a split-view pane) matters more than the window size.
extension BreakpointContext on BuildContext {
  /// Current [Breakpoint] derived from window width.
  Breakpoint get breakpoint =>
      Breakpoints.fromWidth(MediaQuery.sizeOf(this).width);

  bool get isMobile => breakpoint == Breakpoint.mobile;
  bool get isTablet => breakpoint == Breakpoint.tablet;
  bool get isDesktop => breakpoint == Breakpoint.desktop;
}
