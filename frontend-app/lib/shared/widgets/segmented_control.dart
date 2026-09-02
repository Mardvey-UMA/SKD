import 'package:flutter/material.dart';

class SegmentedControl extends StatefulWidget {
  const SegmentedControl({
    super.key,
    required this.labels,
    required this.selectedIndex,
    required this.onChanged,
    this.icons,
  });

  final List<String> labels;
  final List<IconData>? icons;
  final int selectedIndex;
  final ValueChanged<int> onChanged;

  @override
  State<SegmentedControl> createState() => _SegmentedControlState();
}

class _SegmentedControlState extends State<SegmentedControl>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _thumbPosition;
  int _prevIndex = 0;

  static const _trackColor = Color(0xFFEEF1F9); // surface.sunken = neutral-100
  static const _thumbColor = Color(0xFFFFFFFF); // surface.base
  static const _activeText = Color(0xFF3D5BFF); // interactive.primary = brand-500
  static const _inactiveText = Color(0xFF485063); // text.secondary = neutral-600

  static const _duration = Duration(milliseconds: 320); // duration-base
  static const _curve = Cubic(0.22, 1.0, 0.36, 1.0); // curves.expressive

  static const List<BoxShadow> _thumbShadow = [
    BoxShadow(
      color: Color(0x0A0F1828), // rgba(15,24,40,0.04)
      offset: Offset(0, 1),
      blurRadius: 2,
    ),
    BoxShadow(
      color: Color(0x080F1828), // rgba(15,24,40,0.03)
      offset: Offset(0, 2),
      blurRadius: 6,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _prevIndex = widget.selectedIndex;
    _controller = AnimationController(vsync: this, duration: _duration);
    _thumbPosition = Tween<double>(
      begin: widget.selectedIndex.toDouble(),
      end: widget.selectedIndex.toDouble(),
    ).animate(CurvedAnimation(parent: _controller, curve: _curve));
  }

  @override
  void didUpdateWidget(SegmentedControl old) {
    super.didUpdateWidget(old);
    if (old.selectedIndex != widget.selectedIndex) {
      _thumbPosition = Tween<double>(
        begin: _prevIndex.toDouble(),
        end: widget.selectedIndex.toDouble(),
      ).animate(CurvedAnimation(parent: _controller, curve: _curve));
      _controller
        ..reset()
        ..forward();
      _prevIndex = widget.selectedIndex;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final totalWidth = constraints.maxWidth;
        final segmentWidth = totalWidth / widget.labels.length;
        const inset = 4.0;
        final thumbWidth = segmentWidth - inset * 2;

        return Container(
          height: 44,
          decoration: const BoxDecoration(
            color: _trackColor,
            borderRadius: BorderRadius.all(Radius.circular(9999)),
          ),
          padding: const EdgeInsets.all(4),
          child: Stack(
            children: [
              // Animated thumb
              AnimatedBuilder(
                animation: _thumbPosition,
                builder: (context, _) {
                  final left = segmentWidth * _thumbPosition.value + inset;
                  return Positioned(
                    left: left,
                    top: 0,
                    bottom: 0,
                    width: thumbWidth,
                    child: Container(
                      decoration: const BoxDecoration(
                        color: _thumbColor,
                        borderRadius: BorderRadius.all(Radius.circular(9999)),
                        boxShadow: _thumbShadow,
                      ),
                    ),
                  );
                },
              ),
              // Tappable segments
              Row(
                children: List.generate(widget.labels.length, (i) {
                  final isActive = i == widget.selectedIndex;
                  return Expanded(
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () => widget.onChanged(i),
                      child: Center(
                        child: SizedBox(
                          width: thumbWidth,
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              if (widget.icons != null) ...[
                                Icon(
                                  widget.icons![i],
                                  size: 14,
                                  color:
                                      isActive ? _activeText : _inactiveText,
                                ),
                                const SizedBox(width: 4),
                                Flexible(
                                  child: Text(
                                    widget.labels[i],
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    softWrap: false,
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                      letterSpacing: 0.004 * 14,
                                      color: isActive
                                          ? _activeText
                                          : _inactiveText,
                                    ),
                                  ),
                                ),
                              ] else
                                Flexible(
                                  child: Text(
                                    widget.labels[i],
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    softWrap: false,
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontSize: 15,
                                      fontWeight: FontWeight.w600,
                                      letterSpacing: 0.004 * 15,
                                      color: isActive
                                          ? _activeText
                                          : _inactiveText,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ],
          ),
        );
      },
    );
  }
}
