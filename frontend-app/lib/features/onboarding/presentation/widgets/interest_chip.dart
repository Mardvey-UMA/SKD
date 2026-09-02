import 'package:flutter/material.dart';

/// §9.6 Chip — inactive/active two-state token styling.
class InterestChip extends StatefulWidget {
  const InterestChip({
    super.key,
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  State<InterestChip> createState() => _InterestChipState();
}

class _InterestChipState extends State<InterestChip> {
  bool _pressed = false;

  static const _ctaPrimary = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF3D5BFF), Color(0xFF7364FF)],
  );

  // elev-1
  static const _elev1 = [
    BoxShadow(
      color: Color(0x0A0F1828), // rgba(15,24,40, 0.04)
      offset: Offset(0, 1),
      blurRadius: 2,
    ),
    BoxShadow(
      color: Color(0x080F1828), // rgba(15,24,40, 0.03)
      offset: Offset(0, 2),
      blurRadius: 6,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    // duration-fast = 220ms, curves.snappy
    const duration = Duration(milliseconds: 220);
    const curve = Cubic(0.32, 0.72, 0, 1.0);

    final content = AnimatedScale(
      scale: _pressed ? 0.94 : 1.0,
      duration: const Duration(milliseconds: 120), // duration-micro
      curve: const Cubic(0.32, 0.72, 0, 1.0),
      child: AnimatedContainer(
        duration: duration,
        curve: curve,
        height: 44,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          gradient: widget.selected ? _ctaPrimary : null,
          color: widget.selected ? null : Colors.white, // surface.base
          borderRadius: BorderRadius.circular(9999), // radius-full
          border: widget.selected
              ? null
              : Border.all(
                  color: const Color(0x1A0F1828), // border.default
                  width: 1.5,
                ),
          boxShadow: widget.selected ? _elev1 : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              widget.icon,
              size: 16,
              color: widget.selected
                  ? Colors.white
                  : const Color(0xFF485063), // text.secondary = neutral-600
            ),
            const SizedBox(width: 4),
            Text(
              widget.label,
              style: TextStyle(
                fontSize: 15, // button
                fontWeight: FontWeight.w600,
                letterSpacing: 0.004 * 15,
                color: widget.selected
                    ? Colors.white // text.onPrimary
                    : const Color(0xFF0E1525), // text.primary = neutral-900
              ),
            ),
          ],
        ),
      ),
    );

    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) {
        setState(() => _pressed = false);
        widget.onTap();
      },
      onTapCancel: () => setState(() => _pressed = false),
      child: content,
    );
  }
}
