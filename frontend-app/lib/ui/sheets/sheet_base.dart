import 'package:flutter/material.dart';

import '../../theme/colors.dart';
import '../../theme/motion.dart';

/// Shared bottom-sheet chrome for `CardMenu` and `AddToSpaceSheet`.
///
/// Sheets are NOT rendered via [showModalBottomSheet] — they live inside
/// `ResponsiveShell` as full-parent overlays (`Positioned.fill` + scrim).
/// The scrim uses `rgba(14,15,13,0.4)`; tapping it calls [onClose]. The
/// sheet itself absorbs pointer events so inner rows do not close the
/// overlay on stray taps.
///
/// The sheet slides up from 100% of its height over
/// [NFMotion.sheetDuration] with [NFMotion.sheetCurve].
class SheetBase extends StatefulWidget {
  const SheetBase({
    super.key,
    required this.onClose,
    required this.child,
  });

  /// Called when the user taps the scrim. Parents own the open/close state.
  final VoidCallback onClose;

  /// Sheet body — rendered below the drag-handle inside a white surface.
  final Widget child;

  @override
  State<SheetBase> createState() => _SheetBaseState();
}

class _SheetBaseState extends State<SheetBase>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Offset> _offset;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: NFMotion.sheetDuration,
    )..forward();
    _offset = Tween<Offset>(
      begin: const Offset(0, 1),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _controller, curve: NFMotion.sheetCurve));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned.fill(
      child: Stack(
        children: <Widget>[
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: widget.onClose,
              child: const ColoredBox(
                color: Color.fromRGBO(14, 15, 13, 0.4),
              ),
            ),
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: SlideTransition(
              position: _offset,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () {},
                child: Material(
                  type: MaterialType.transparency,
                  child: Container(
                    width: double.infinity,
                    decoration: const BoxDecoration(
                      color: NFColors.surface,
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.circular(24),
                        topRight: Radius.circular(24),
                      ),
                    ),
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 20),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: <Widget>[
                        const _DragHandle(),
                        Flexible(child: widget.child),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DragHandle extends StatelessWidget {
  const _DragHandle();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 4, bottom: 12),
      child: Center(
        child: Container(
          width: 44,
          height: 4,
          decoration: const BoxDecoration(
            color: NFColors.hairline,
            borderRadius: BorderRadius.all(Radius.circular(4)),
          ),
        ),
      ),
    );
  }
}
