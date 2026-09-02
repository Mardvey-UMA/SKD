import 'package:flutter/widgets.dart';

import '_android_common.dart';

/// Pixel-style Android bezel ported from
/// `design/reference/mockup/device-frame.jsx` § PixelDevice.
///
/// 402×874, radius 44, 12-px centred punch-hole camera.
class PixelDevice extends StatelessWidget {
  const PixelDevice({
    super.key,
    required this.child,
    this.width = 402,
    this.height = 874,
    this.time = '9:41',
  });

  final Widget child;
  final double width;
  final double height;
  final String time;

  static const Color _shell = Color(0xFFF7F7F4);

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: _shell,
        borderRadius: BorderRadius.circular(44),
        boxShadow: const <BoxShadow>[
          BoxShadow(
            offset: Offset(0, 40),
            blurRadius: 80,
            color: Color.fromRGBO(0, 0, 0, 0.18),
          ),
          BoxShadow(
            blurRadius: 0,
            spreadRadius: 1.5,
            color: Color.fromRGBO(0, 0, 0, 0.18),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: child),
          // Status bar.
          const Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: AndroidStatus(),
          ),
          // Punch-hole camera — 12-px, centred, 10-px from top.
          Positioned(
            top: 10,
            left: 0,
            right: 0,
            child: Center(
              child: Container(
                width: 12,
                height: 12,
                decoration: const BoxDecoration(
                  color: Color(0xFF111111),
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
          // Gesture bar.
          const Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: AndroidGestureBar(),
          ),
        ],
      ),
    );
  }
}
