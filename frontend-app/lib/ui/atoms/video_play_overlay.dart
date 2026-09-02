import 'package:flutter/material.dart';

import '../../theme/colors.dart';

/// Centred play-glyph shown on top of an image block when the media
/// entry is a video. Purely decorative — the card already opens the
/// detail screen on tap, which is where the video actually plays.
class VideoPlayOverlay extends StatelessWidget {
  const VideoPlayOverlay({super.key, this.size = 64});

  final double size;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: NFColors.ink.withValues(alpha: 0.78),
          shape: BoxShape.circle,
          boxShadow: <BoxShadow>[
            BoxShadow(
              color: const Color(0x55000000),
              blurRadius: 12,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        alignment: Alignment.center,
        // Offset the triangle 2 px right so its visual centre sits at
        // the circle centre (triangle baseline bias).
        child: Padding(
          padding: const EdgeInsets.only(left: 4),
          child: Icon(
            Icons.play_arrow_rounded,
            color: NFColors.accentInk,
            size: size * 0.55,
          ),
        ),
      ),
    );
  }
}
