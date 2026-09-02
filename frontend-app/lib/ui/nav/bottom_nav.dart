import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../../theme/motion.dart';
import '../../theme/shadows.dart';
import '../atoms/nf_icon.dart';
import '../motion/press_scale.dart';

/// The four tabs exposed by [BottomNav], in left-to-right render order.
///
/// Labels and icon asset names mirror `TABS` in
/// `design/reference/mockup/radar-web.html` (`feed / collections / profile /
/// settings`).
enum BottomNavTab {
  feed,
  collections,
  profile,
  settings,
}

/// Floating bottom navigation pill for mobile / tablet layouts.
///
/// Mirrors `BottomNav` in `design/reference/mockup/radar-web.html` (§ lines
/// 135–166): a pill-shaped [NFColors.surface] container with hairline border,
/// [NFShadows.bottomNav] elevation, 6-px inner padding, and a [Row] of four
/// [Expanded] tabs.
///
/// * Positioning (12-px from left / right / bottom) is owned by the parent
///   `ResponsiveShell` — this widget simply fills the space given to it.
/// * **No `backdrop-filter: blur`** — Risk-flag #1 (CanvasKit FPS drop).
/// * Active tab: [NFColors.ink] fill, white icon, [NFColors.lime] label.
/// * Inactive tab: transparent fill, [NFColors.ink2] icon, [NFColors.mute]
///   label.
/// * Press-down scales the tapped tab to `0.92` via the shared [PressScale]
///   micro-interaction (Prompt 24).
/// * Tapping an already-active tab fires [onRetap] (reserved for future
///   scroll-to-top behaviour); taps on inactive tabs fire [onTab].
class BottomNav extends StatelessWidget {
  const BottomNav({
    super.key,
    required this.activeIndex,
    required this.onTab,
    this.onRetap,
  }) : assert(
          activeIndex >= 0 && activeIndex < 4,
          'activeIndex must be in [0, 3]',
        );

  /// Index of the currently active tab (0..3), matching [BottomNavTab.values].
  final int activeIndex;

  /// Fired when the user taps a tab that is NOT the active one. The supplied
  /// index is the newly-selected tab.
  final ValueChanged<int> onTab;

  /// Fired when the user taps the already-active tab. Reserved for
  /// scroll-to-top handling by the screen above.
  final ValueChanged<int>? onRetap;

  static const List<_TabSpec> _tabs = <_TabSpec>[
    _TabSpec(icon: 'feed', label: 'Лента'),
    _TabSpec(icon: 'layers', label: 'Подборки'),
    _TabSpec(icon: 'user', label: 'Профиль'),
    _TabSpec(icon: 'gear', label: 'Настройки'),
  ];

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: NFColors.hairline, width: 1),
        boxShadow: NFShadows.bottomNav,
      ),
      child: Padding(
        padding: const EdgeInsets.all(6),
        child: Row(
          children: <Widget>[
            for (int i = 0; i < _tabs.length; i++)
              Expanded(
                child: _BottomNavTabButton(
                  spec: _tabs[i],
                  active: i == activeIndex,
                  onTap: () {
                    if (i == activeIndex) {
                      onRetap?.call(i);
                    } else {
                      onTab(i);
                    }
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _TabSpec {
  const _TabSpec({required this.icon, required this.label});

  final String icon;
  final String label;
}

class _BottomNavTabButton extends StatelessWidget {
  const _BottomNavTabButton({
    required this.spec,
    required this.active,
    required this.onTap,
  });

  final _TabSpec spec;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final bool on = active;
    final Color iconColor = on ? NFColors.lime : NFColors.ink2;

    return Semantics(
      label: spec.label,
      button: true,
      selected: on,
      child: PressScale(
        onTap: onTap,
        duration: NFMotion.fastDuration,
        child: AnimatedContainer(
          duration: NFMotion.navDuration,
          curve: NFMotion.navCurve,
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: const BoxDecoration(
            color: Color(0x00000000),
          ),
          child: Center(
            child: AnimatedContainer(
              duration: NFMotion.navDuration,
              curve: NFMotion.navCurve,
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                boxShadow: on
                    ? const <BoxShadow>[
                        BoxShadow(
                          color: Color(0x33CCFF33),
                          blurRadius: 14,
                          spreadRadius: 0,
                        ),
                      ]
                    : const <BoxShadow>[],
              ),
              alignment: Alignment.center,
              child: NFIcon(spec.icon, size: 24, color: iconColor),
            ),
          ),
        ),
      ),
    );
  }
}
