import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/widgets.dart';

import 'image_item.dart';
import 'stripe_placeholder.dart';

/// Multi-image card variant. Layout depends on how many URLs arrived
/// in [ImageItem.imageUrls]:
///
/// * 2 images → 1+1 vertical split (50/50).
/// * 3 images → 1 tall on the left (flex 2) + 2 stacked on the right
///   (flex 1 each). Mirrors the original mockup contract.
/// * 4+ images → 2×2 grid; the last tile shows a «+N» overlay when
///   `imageUrls.length > 4`, Telegram-style.
///
/// Each cell renders a `CachedNetworkImage` when its URL is resolved,
/// falling back to a tone-matched [StripePlaceholder]. Outer 16-px
/// rounded clip; inner cells unrounded with 4-px gutters.
class MultiImage extends StatelessWidget {
  const MultiImage({
    super.key,
    required this.item,
    this.height = 200,
  });

  final ImageItem item;
  final double height;

  @override
  Widget build(BuildContext context) {
    final List<StripeTone> tones = <StripeTone>[
      item.tone,
      item.toneSecondary ?? StripeTone.accent,
      item.toneTertiary ?? StripeTone.lime,
      StripeTone.rose,
    ];
    final int count = item.imageUrls.length;
    if (count <= 1) {
      // Shouldn't reach here — SingleImage handles 0/1.
      return const SizedBox.shrink();
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: SizedBox(
        height: height,
        child: switch (count) {
          2 => _layout2(tones),
          3 => _layout3(tones),
          _ => _layout4(tones, count),
        },
      ),
    );
  }

  Widget _layout2(List<StripeTone> tones) {
    return Row(
      children: <Widget>[
        Expanded(child: _Cell(tone: tones[0], seed: item.seed, label: '1/2', url: item.imageUrls[0])),
        const SizedBox(width: 4),
        Expanded(child: _Cell(tone: tones[1], seed: item.seed + 10, label: '2/2', url: item.imageUrls[1])),
      ],
    );
  }

  Widget _layout3(List<StripeTone> tones) {
    return Row(
      children: <Widget>[
        Expanded(
          flex: 2,
          child: _Cell(
            tone: tones[0],
            seed: item.seed,
            label: '1/3',
            url: item.imageUrls[0],
          ),
        ),
        const SizedBox(width: 4),
        Expanded(
          flex: 1,
          child: Column(
            children: <Widget>[
              Expanded(child: _Cell(tone: tones[1], seed: item.seed + 10, label: '2/3', url: item.imageUrls[1])),
              const SizedBox(height: 4),
              Expanded(child: _Cell(tone: tones[2], seed: item.seed + 20, label: '3/3', url: item.imageUrls[2])),
            ],
          ),
        ),
      ],
    );
  }

  Widget _layout4(List<StripeTone> tones, int totalCount) {
    final int extra = totalCount > 4 ? totalCount - 4 : 0;
    return Column(
      children: <Widget>[
        Expanded(
          child: Row(
            children: <Widget>[
              Expanded(child: _Cell(tone: tones[0], seed: item.seed, label: '1/$totalCount', url: item.imageUrls[0])),
              const SizedBox(width: 4),
              Expanded(child: _Cell(tone: tones[1], seed: item.seed + 10, label: '2/$totalCount', url: item.imageUrls[1])),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          child: Row(
            children: <Widget>[
              Expanded(child: _Cell(tone: tones[2], seed: item.seed + 20, label: '3/$totalCount', url: item.imageUrls[2])),
              const SizedBox(width: 4),
              Expanded(
                child: Stack(
                  fit: StackFit.expand,
                  children: <Widget>[
                    _Cell(
                      tone: tones[3],
                      seed: item.seed + 30,
                      label: '4/$totalCount',
                      url: item.imageUrls[3],
                    ),
                    if (extra > 0)
                      IgnorePointer(
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: const Color(0xAA000000),
                          ),
                          child: Center(
                            child: Text(
                              '+$extra',
                              style: const TextStyle(
                                fontFamily: 'Nunito',
                                fontSize: 22,
                                fontWeight: FontWeight.w800,
                                color: Color(0xFFFFFFFF),
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Cell extends StatelessWidget {
  const _Cell({
    required this.tone,
    required this.seed,
    required this.label,
    required this.url,
  });

  final StripeTone tone;
  final int seed;
  final String label;
  final String? url;

  @override
  Widget build(BuildContext context) {
    final Widget placeholder = StripePlaceholder(
      height: double.infinity,
      tone: tone,
      seed: seed,
      label: label,
      radius: 0,
    );
    if (url == null || url!.isEmpty) return placeholder;
    return CachedNetworkImage(
      imageUrl: url!,
      fit: BoxFit.cover,
      placeholder: (_, _) => placeholder,
      errorWidget: (_, _, _) => placeholder,
      fadeInDuration: const Duration(milliseconds: 120),
    );
  }
}
