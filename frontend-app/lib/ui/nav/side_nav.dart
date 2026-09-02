import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../../theme/motion.dart';
import '../atoms/nf_icon.dart';
import '../motion/press_scale.dart';

/// Desktop-only left side navigation column.
///
/// Mirrors `SideNav` in `design/reference/mockup/radar-web.html` (§ lines
/// 169–217): 240-px wide, `EdgeInsets.symmetric(vertical: 28, horizontal: 16)`
/// padding, [NFColors.bg] fill, hairline right border. The widget itself
/// does NOT scroll — scrolling belongs to the adjacent `main` column owned
/// by the shell.
///
/// Header: 32×32 logo mark rendered from `assets/icons/radar.svg` via
/// [NFIcon] (diamond-with-ring) + the wordmark «Радар» (20 px · `w800` ·
/// letter-spacing −0.8), padding `0, 6, 20, 6`.
///
/// Four tabs (`feed / collections / profile / settings`) — identical set to
/// `BottomNav` (P-11) — with Russian labels `Лента / Подборки / Профиль /
/// Настройки`.
///
/// * Active tab: [NFColors.ink] fill, white foreground.
/// * Inactive tab: transparent fill, [NFColors.ink] foreground.
/// * Hover: bg transitions 140 ms ease via [MouseRegion] + [AnimatedContainer].
/// * Press: 0.92 scale via shared [PressScale] micro-interaction (Prompt 24).
///
/// Placement (`position: sticky`, `height: 100vh`, `alignSelf: flex-start`)
/// is owned by the parent `ResponsiveShell`.
class SideNav extends StatelessWidget {
  const SideNav({
    super.key,
    required this.activeIndex,
    required this.onTab,
  }) : assert(
          activeIndex >= 0 && activeIndex < 4,
          'activeIndex must be in [0, 3]',
        );

  /// Index of the currently active tab (0..3).
  final int activeIndex;

  /// Fired when the user taps a tab. The supplied index is the tapped tab
  /// (including re-tap of the active one — parent decides behaviour).
  final ValueChanged<int> onTab;

  static const List<_TabSpec> _tabs = <_TabSpec>[
    _TabSpec(icon: 'feed', label: 'Лента'),
    _TabSpec(icon: 'layers', label: 'Подборки'),
    _TabSpec(icon: 'user', label: 'Профиль'),
    _TabSpec(icon: 'gear', label: 'Настройки'),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 240,
      padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
      decoration: const BoxDecoration(
        color: NFColors.bg,
        border: Border(
          right: BorderSide(color: NFColors.hairline, width: 1),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          const _SideNavHeader(),
          for (int i = 0; i < _tabs.length; i++) ...<Widget>[
            if (i > 0) const SizedBox(height: 2),
            _SideNavTabButton(
              spec: _tabs[i],
              active: i == activeIndex,
              onTap: () => onTab(i),
            ),
          ],
        ],
      ),
    );
  }
}

class _TabSpec {
  const _TabSpec({required this.icon, required this.label});

  final String icon;
  final String label;
}

class _SideNavHeader extends StatelessWidget {
  const _SideNavHeader();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.fromLTRB(6, 0, 6, 20),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          NFIcon('radar', size: 32),
          SizedBox(width: 10),
          Text(
            'Радар',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 20,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.8,
              color: NFColors.ink,
              height: 1.0,
            ),
          ),
        ],
      ),
    );
  }
}

class _SideNavTabButton extends StatefulWidget {
  const _SideNavTabButton({
    required this.spec,
    required this.active,
    required this.onTap,
  });

  final _TabSpec spec;
  final bool active;
  final VoidCallback onTap;

  @override
  State<_SideNavTabButton> createState() => _SideNavTabButtonState();
}

class _SideNavTabButtonState extends State<_SideNavTabButton> {
  bool _hovered = false;

  void _setHovered(bool value) {
    if (_hovered == value) return;
    setState(() => _hovered = value);
  }

  @override
  Widget build(BuildContext context) {
    final bool on = widget.active;
    final Color fg = on ? const Color(0xFFFFFFFF) : NFColors.ink;
    // Hover tint (only when inactive): mirrors the mockup's 140ms ease bg
    // fade toward `ink`, rendered at 6% opacity for a subtle highlight.
    final Color fill = on
        ? NFColors.ink
        : (_hovered
            ? const Color.fromRGBO(14, 15, 13, 0.06)
            : const Color(0x00000000));

    return Semantics(
      label: widget.spec.label,
      button: true,
      selected: on,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => _setHovered(true),
        onExit: (_) => _setHovered(false),
        child: PressScale(
          onTap: widget.onTap,
          duration: NFMotion.fastDuration,
          child: AnimatedContainer(
            duration: NFMotion.navDuration,
            curve: NFMotion.navCurve,
            padding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 11,
            ),
            decoration: BoxDecoration(
              color: fill,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                NFIcon(widget.spec.icon, size: 18, color: fg),
                const SizedBox(width: 12),
                Text(
                  widget.spec.label,
                  style: TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    letterSpacing: -0.2,
                    color: fg,
                    height: 1.0,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
