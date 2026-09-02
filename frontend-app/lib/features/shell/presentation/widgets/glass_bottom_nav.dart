import 'dart:ui';

import 'package:flutter/material.dart';

import '../../../../theme/theme_context_extension.dart';

class GlassBottomNav extends StatelessWidget {
  const GlassBottomNav({
    super.key,
    required this.currentIndex,
    required this.onTap,
  });

  final int currentIndex;
  final ValueChanged<int> onTap;

  static const _items = [
    _NavItemData(icon: Icons.home_outlined, activeIcon: Icons.home, label: 'Лента'),
    _NavItemData(icon: Icons.collections_bookmark_outlined, activeIcon: Icons.collections_bookmark, label: 'Коллекции'),
    _NavItemData(icon: Icons.person_outline, activeIcon: Icons.person, label: 'Профиль'),
    _NavItemData(icon: Icons.settings_outlined, activeIcon: Icons.settings, label: 'Настройки'),
  ];

  @override
  Widget build(BuildContext context) {
    final safeBottom = MediaQuery.of(context).padding.bottom;

    return RepaintBoundary(
      child: SizedBox(
        height: 72 + safeBottom,
        child: ClipRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 32, sigmaY: 32),
            child: Container(
              decoration: BoxDecoration(
                gradient: AppGradients.glassStrong,
                border: const Border(
                  top: BorderSide(
                    color: Color(0xB3FFFFFF), // rgba(255,255,255, 0.7)
                    width: 1,
                  ),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x0F0F1828), // rgba(15,24,40, 0.06)
                    offset: Offset(0, -8),
                    blurRadius: 32,
                  ),
                ],
              ),
              child: SafeArea(
                top: false,
                child: SizedBox(
                  height: 72,
                  child: Row(
                    children: List.generate(
                      _items.length,
                      (i) => Expanded(
                        child: _NavItem(
                          data: _items[i],
                          isActive: currentIndex == i,
                          onTap: () => onTap(i),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NavItemData {
  const _NavItemData({
    required this.icon,
    required this.activeIcon,
    required this.label,
  });

  final IconData icon;
  final IconData activeIcon;
  final String label;
}

class _NavItem extends StatefulWidget {
  const _NavItem({
    required this.data,
    required this.isActive,
    required this.onTap,
  });

  final _NavItemData data;
  final bool isActive;
  final VoidCallback onTap;

  @override
  State<_NavItem> createState() => _NavItemState();
}

class _NavItemState extends State<_NavItem> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.tokens.colors;

    final iconColor = widget.isActive
        ? colors.interactive.primary
        : colors.text.tertiary;

    return Semantics(
      label: widget.data.label,
      button: true,
      selected: widget.isActive,
      child: GestureDetector(
        onTap: widget.onTap,
        onTapDown: (_) => setState(() => _pressed = true),
        onTapUp: (_) => setState(() => _pressed = false),
        onTapCancel: () => setState(() => _pressed = false),
        behavior: HitTestBehavior.opaque,
        child: AnimatedScale(
        scale: _pressed ? 0.94 : 1.0,
        duration: _pressed ? AppMotion.micro : AppMotion.fast,
        curve: _pressed ? AppMotion.snappy : AppMotion.standard,
        child: SizedBox(
          width: double.infinity,
          height: 72,
          child: Center(
            child: AnimatedContainer(
              duration: AppMotion.base,
              curve: AppMotion.expressive,
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: widget.isActive
                    ? colors.interactive.primarySubtle
                    : Colors.transparent,
                borderRadius: AppRadii.bfull,
              ),
              child: Icon(
                widget.isActive
                    ? widget.data.activeIcon
                    : widget.data.icon,
                size: 28,
                color: iconColor,
              ),
            ),
          ),
        ),
        ),
      ),
    );
  }
}
