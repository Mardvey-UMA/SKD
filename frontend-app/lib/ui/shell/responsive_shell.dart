import 'package:flutter/material.dart';

import '../../responsive/breakpoint.dart';
import '../../theme/colors.dart';
import '../../theme/shadows.dart';
import '../nav/bottom_nav.dart';
import '../nav/side_nav.dart';
import 'device_frame.dart';

/// Responsive application shell that swaps layout per [Breakpoint].
///
/// Three branches (thresholds live in [Breakpoints]):
///
/// * **mobile** (`< 720`): full-bleed [child] with a floating [BottomNav]
///   overlayed 12-px from the left / right / bottom edges. No phone chrome.
/// * **tablet** (`720–1023`): [child] sits inside a 520-max-width surface
///   panel (radius 28, hairline border, [NFShadows.tabletPanel]). [BottomNav]
///   overlays the panel 12-px from its edges.
/// * **desktop** (`≥ 1024`): a [Row] of [SideNav] + [Expanded(child)]. No
///   [BottomNav].
///
/// [showBottomNav] is honoured only on mobile / tablet — desktop uses the
/// always-visible [SideNav]. Positioning of the floating nav is owned here,
/// not by [BottomNav] itself.
class ResponsiveShell extends StatelessWidget {
  const ResponsiveShell({
    super.key,
    required this.child,
    required this.bp,
    required this.activeTab,
    required this.onTab,
    this.showBottomNav = true,
    this.deviceKind = DeviceKind.iphone,
  });

  final Widget child;
  final Breakpoint bp;
  final int activeTab;
  final ValueChanged<int> onTab;
  final bool showBottomNav;

  /// Which device bezel [DeviceFrame] renders on mobile. Exposed so tests
  /// can swap to [DeviceKind.pixel] / [DeviceKind.galaxy] without touching
  /// production defaults.
  final DeviceKind deviceKind;

  @override
  Widget build(BuildContext context) {
    final Widget layout = switch (bp) {
      Breakpoint.mobile => _buildMobile(),
      Breakpoint.tablet => _buildTablet(),
      Breakpoint.desktop => _buildDesktop(),
    };
    return Material(
      type: MaterialType.transparency,
      child: layout,
    );
  }

  Widget _buildMobile() {
    // Full-bleed on real mobile browsers — no decorative phone chrome,
    // no outer 28/16 margin. The `DeviceFrame` preview is kept only as
    // an explicit opt-in for designer screenshots (see `deviceKind`
    // parameter threaded through test hooks).
    return ColoredBox(
      color: NFColors.bg,
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: child),
          if (showBottomNav)
            Positioned(
              left: 12,
              right: 12,
              bottom: 12,
              child: BottomNav(activeIndex: activeTab, onTab: onTab),
            ),
        ],
      ),
    );
  }

  Widget _buildTablet() {
    return ColoredBox(
      color: NFColors.bg,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: NFColors.bg,
                borderRadius: BorderRadius.circular(28),
                border: Border.all(color: NFColors.hairline, width: 1),
                boxShadow: NFShadows.tabletPanel,
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(28),
                child: Stack(
                  children: <Widget>[
                    Positioned.fill(child: child),
                    if (showBottomNav)
                      Positioned(
                        left: 12,
                        right: 12,
                        bottom: 12,
                        child: BottomNav(activeIndex: activeTab, onTab: onTab),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDesktop() {
    return ColoredBox(
      color: NFColors.bg,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          SideNav(activeIndex: activeTab, onTab: onTab),
          Expanded(child: child),
        ],
      ),
    );
  }
}
