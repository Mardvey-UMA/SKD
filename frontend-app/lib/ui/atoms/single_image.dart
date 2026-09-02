import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/widgets.dart';

import 'image_item.dart';
import 'stripe_placeholder.dart';

/// Single-image card variant — one full-bleed image wrapped in a 16 px
/// rounded clip. When [ImageItem.imageUrl] is non-null, renders a
/// `CachedNetworkImage`; otherwise falls back to a pure
/// [StripePlaceholder]. Placeholder + error states both reuse the stripe
/// pattern so load state is visually consistent.
///
/// Mirrors `SingleImage` in `design/reference/mockup/cards.jsx`.
class SingleImage extends StatelessWidget {
  const SingleImage({
    super.key,
    required this.item,
    this.height = 200,
  });

  final ImageItem item;
  final double height;

  @override
  Widget build(BuildContext context) {
    final Widget placeholder = StripePlaceholder(
      height: height,
      // Darker tone for video so the play-overlay contrasts better.
      tone: item.isVideo ? StripeTone.ink : item.tone,
      seed: item.seed,
      label: item.isVideo ? 'ВИДЕО' : 'ФОТО',
      radius: 0,
    );
    final String? url = item.imageUrl;
    final Widget child = (url == null || url.isEmpty)
        ? placeholder
        : SizedBox(
            height: height,
            width: double.infinity,
            child: CachedNetworkImage(
              imageUrl: url,
              fit: BoxFit.cover,
              placeholder: (_, _) => placeholder,
              errorWidget: (_, _, _) => placeholder,
              fadeInDuration: const Duration(milliseconds: 120),
            ),
          );
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: child,
    );
  }
}
