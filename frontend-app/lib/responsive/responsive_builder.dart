import 'package:flutter/widgets.dart';

import 'breakpoint.dart';

/// Signature for [ResponsiveBuilder.builder].
typedef ResponsiveWidgetBuilder = Widget Function(
  BuildContext context,
  Breakpoint breakpoint,
);

/// Wraps [LayoutBuilder] and resolves a [Breakpoint] from the *local*
/// constraint width (not the window width).
///
/// Use this when a nested constraint matters more than the window size — for
/// example, when a widget lives inside a split pane, a sidebar column, or a
/// fixed-width device frame. For plain window-size decisions, prefer
/// `context.breakpoint` from `context_ext.dart`.
class ResponsiveBuilder extends StatelessWidget {
  const ResponsiveBuilder({
    super.key,
    required this.builder,
  });

  final ResponsiveWidgetBuilder builder;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final breakpoint = Breakpoints.fromWidth(constraints.maxWidth);
        return builder(context, breakpoint);
      },
    );
  }
}
