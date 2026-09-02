import 'package:flutter/widgets.dart';

import '_android_common.dart';

/// Galaxy-style Android bezel ported from
/// `design/reference/mockup/device-frame.jsx` § GalaxyDevice.
///
/// 402×874, radius 52, 10-px centred punch-hole camera, thicker inky border.
class GalaxyDevice extends StatelessWidget {
  const GalaxyDevice({
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

  static const Color _shell = Color(0xFFF2F3F5);

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: _shell,
        borderRadius: BorderRadius.circular(52),
        boxShadow: const <BoxShadow>[
          BoxShadow(
            offset: Offset(0, 40),
            blurRadius: 80,
            color: Color.fromRGBO(0, 0, 0, 0.2),
          ),
          BoxShadow(
            blurRadius: 0,
            spreadRadius: 2,
            color: Color(0xFF111111),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: child),
          const Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: AndroidStatus(),
          ),
          // Slightly smaller + higher punch-hole (10×10 at top:9).
          Positioned(
            top: 9,
            left: 0,
            right: 0,
            child: Center(
              child: Container(
                width: 10,
                height: 10,
                decoration: const BoxDecoration(
                  color: Color(0xFF111111),
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
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
