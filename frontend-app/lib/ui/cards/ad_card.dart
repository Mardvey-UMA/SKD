import 'package:flutter/widgets.dart';

import '../../models/ad.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../atoms/hatched_painter.dart';
import '../atoms/nf_icon.dart';
import '../atoms/nf_text.dart';

/// Visual treatment for a sponsored [Ad].
///
/// Maps 1:1 to the JSX `style` prop on `AdCard`:
/// * [off] — feature flag value; widget should not be rendered.
/// * [subtle] — 4-px lime accent strip on an otherwise regular card.
/// * [card] — full card with a hatched lime header + giant brand wordmark
///   and an inky CTA pill.
/// * [banner] — one-row banner with a 54×54 lime hatched icon tile.
enum AdStyle { off, subtle, card, banner }

/// Sponsored content card with three visual styles.
///
/// Mirrors `AdCard` in `design/reference/mockup/cards.jsx`. Shared across
/// the feed alongside `ShortCard` / `LongCard`. All three visible styles
/// surface the same «РЕКЛАМА · BRAND» mono-label with a 5×5 lime dot.
///
/// [onClick] fires on any area of the card except the close button. The
/// close button fires [onHide] and stops propagation — matching the JSX
/// `e.stopPropagation()` contract.
class AdCard extends StatelessWidget {
  const AdCard({
    super.key,
    required this.ad,
    this.style = AdStyle.subtle,
    this.onClick,
    this.onHide,
  });

  final Ad ad;
  final AdStyle style;

  /// Fired on tap of the card body (everywhere except the close button).
  final ValueChanged<Ad>? onClick;

  /// Fired on tap of the close button. Does NOT bubble to [onClick].
  final ValueChanged<Ad>? onHide;

  @override
  Widget build(BuildContext context) {
    switch (style) {
      case AdStyle.off:
        return const SizedBox.shrink();
      case AdStyle.subtle:
        return _SubtleAd(ad: ad, onClick: onClick, onHide: onHide);
      case AdStyle.card:
        return _CardAd(ad: ad, onClick: onClick, onHide: onHide);
      case AdStyle.banner:
        return _BannerAd(ad: ad, onClick: onClick, onHide: onHide);
    }
  }
}

/// Shared «РЕКЛАМА · BRAND» label row with a 5×5 lime dot and a 28×28 close
/// button aligned right.
class _AdLabel extends StatelessWidget {
  const _AdLabel({required this.brand, required this.onHide});

  final String brand;
  final VoidCallback? onHide;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        Expanded(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const _LimeDot(),
              const SizedBox(width: 8),
              Flexible(
                child: NFText.mono(
                  'РЕКЛАМА · $brand',
                  color: NFColors.ink2,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
        _CloseButton(size: 28, iconSize: 14, onTap: onHide),
      ],
    );
  }
}

class _LimeDot extends StatelessWidget {
  const _LimeDot();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 5,
      height: 5,
      decoration: const BoxDecoration(
        color: NFColors.lime,
        shape: BoxShape.circle,
      ),
    );
  }
}

class _CloseButton extends StatelessWidget {
  const _CloseButton({
    required this.size,
    required this.iconSize,
    required this.onTap,
  });

  final double size;
  final double iconSize;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Скрыть рекламу',
      button: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: SizedBox(
          width: size,
          height: size,
          child: Center(
            child: Opacity(
              opacity: 0.55,
              child: NFIcon('close', size: iconSize, color: NFColors.ink),
            ),
          ),
        ),
      ),
    );
  }
}

class _SubtleAd extends StatelessWidget {
  const _SubtleAd({required this.ad, required this.onClick, required this.onHide});

  final Ad ad;
  final ValueChanged<Ad>? onClick;
  final ValueChanged<Ad>? onHide;

  static const TextStyle _titleStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 18,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.4,
    height: 1.25,
    color: NFColors.ink,
  );

  static const TextStyle _descStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 13.5,
    fontWeight: FontWeight.w400,
    height: 1.45,
    color: NFColors.mute,
  );

  @override
  Widget build(BuildContext context) {
    final Widget content = DecoratedBox(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.hairline, width: 1),
      ),
      child: ClipRRect(
        borderRadius: NFRadii.brLg,
        child: Stack(
          children: <Widget>[
            const Positioned(
              left: 0,
              top: 0,
              bottom: 0,
              width: 4,
              child: ColoredBox(color: NFColors.lime),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 14, 14, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  _AdLabel(
                    brand: ad.brand,
                    onHide: onHide == null ? null : () => onHide!(ad),
                  ),
                  const SizedBox(height: 8),
                  Text(ad.title, style: _titleStyle),
                  const SizedBox(height: 6),
                  Text(ad.tagline, style: _descStyle),
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: _CtaPill(label: ad.cta),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onClick == null ? null : () => onClick!(ad),
      child: content,
    );
  }
}

class _CardAd extends StatelessWidget {
  const _CardAd({required this.ad, required this.onClick, required this.onHide});

  final Ad ad;
  final ValueChanged<Ad>? onClick;
  final ValueChanged<Ad>? onHide;

  static const TextStyle _wordmarkStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 34,
    fontWeight: FontWeight.w800,
    letterSpacing: -1.2,
    height: 1.0,
    color: NFColors.limeInk,
  );

  static const TextStyle _titleStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 18,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.4,
    height: 1.25,
    color: NFColors.ink,
  );

  static const TextStyle _descStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 13.5,
    fontWeight: FontWeight.w400,
    height: 1.45,
    color: NFColors.mute,
  );

  @override
  Widget build(BuildContext context) {
    final Widget content = DecoratedBox(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.lime, width: 1.5),
      ),
      child: ClipRRect(
        borderRadius: NFRadii.brLg,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            SizedBox(
              height: 88,
              child: Stack(
                fit: StackFit.expand,
                children: <Widget>[
                  const ColoredBox(color: NFColors.lime),
                  const Positioned.fill(
                    child: CustomPaint(painter: HatchedPainter()),
                  ),
                  Positioned(
                    right: 14,
                    top: 14,
                    child: Opacity(
                      opacity: 0.85,
                      child: Text(ad.brand, style: _wordmarkStyle),
                    ),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  _AdLabel(
                    brand: ad.brand,
                    onHide: onHide == null ? null : () => onHide!(ad),
                  ),
                  const SizedBox(height: 8),
                  Text(ad.title, style: _titleStyle),
                  const SizedBox(height: 6),
                  Text(ad.desc, style: _descStyle),
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: _CtaPill(label: ad.cta),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onClick == null ? null : () => onClick!(ad),
      child: content,
    );
  }
}

class _BannerAd extends StatelessWidget {
  const _BannerAd({required this.ad, required this.onClick, required this.onHide});

  final Ad ad;
  final ValueChanged<Ad>? onClick;
  final ValueChanged<Ad>? onHide;

  static const TextStyle _initialStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 22,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.6,
    height: 1.0,
    color: NFColors.limeInk,
  );

  static const TextStyle _titleStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 15,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.3,
    height: 1.3,
    color: NFColors.ink,
  );

  static const TextStyle _taglineStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 12.5,
    fontWeight: FontWeight.w400,
    height: 1.35,
    color: NFColors.mute,
  );

  @override
  Widget build(BuildContext context) {
    final String brandInitial =
        ad.brand.isEmpty ? '' : ad.brand.characters.first.toUpperCase();

    final Widget content = DecoratedBox(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.hairline, width: 1),
      ),
      child: ClipRRect(
        borderRadius: NFRadii.brLg,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              _BannerTile(initial: brandInitial, style: _initialStyle),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        const _LimeDot(),
                        const SizedBox(width: 6),
                        Flexible(
                          child: NFText.mono(
                            'РЕКЛАМА · ${ad.brand}',
                            color: NFColors.ink2,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 3),
                    Text(
                      ad.title,
                      style: _titleStyle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      ad.tagline,
                      style: _taglineStyle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              _CloseButton(
                size: 26,
                iconSize: 13,
                onTap: onHide == null ? null : () => onHide!(ad),
              ),
            ],
          ),
        ),
      ),
    );

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onClick == null ? null : () => onClick!(ad),
      child: content,
    );
  }
}

class _BannerTile extends StatelessWidget {
  const _BannerTile({required this.initial, required this.style});

  final String initial;
  final TextStyle style;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 54,
      height: 54,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: NFColors.lime,
          borderRadius: BorderRadius.circular(14),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(14),
          child: Stack(
            fit: StackFit.expand,
            children: <Widget>[
              const Positioned.fill(
                child: CustomPaint(
                  painter: HatchedPainter(
                    period: 9,
                    strokeWidth: 1,
                    color: Color(0x140E0F0D),
                  ),
                ),
              ),
              Center(child: Text(initial, style: style)),
            ],
          ),
        ),
      ),
    );
  }
}

class _CtaPill extends StatelessWidget {
  const _CtaPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: const BoxDecoration(
          color: NFColors.ink,
          borderRadius: BorderRadius.all(Radius.circular(999)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(
              label,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 13.5,
                fontWeight: FontWeight.w600,
                letterSpacing: -0.2,
                color: Color(0xFFFFFFFF),
              ),
            ),
            const SizedBox(width: 6),
            const NFIcon('arrow-right', size: 13, color: Color(0xFFFFFFFF)),
          ],
        ),
      ),
    );
  }
}
