import 'dart:ui' show PathMetric;

import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../atoms/nf_icon.dart';
import '../atoms/nf_text.dart';
import 'sheet_base.dart';

/// User-owned collection metadata rendered in [AddToSpaceSheet] rows.
///
/// Minimal shape — sheet only needs id, title and an optional colour tone
/// for the 36×36 swatch. Consuming features remap their richer domain
/// models to this DTO at the call-site.
@immutable
class UserCollection {
  const UserCollection({
    required this.id,
    required this.title,
    this.tone = CollectionTone.ink,
  });

  final String id;
  final String title;
  final CollectionTone tone;
}

/// Colour tones for the 36×36 swatch, mirroring the JSX `c.tone` switch.
enum CollectionTone {
  lime(NFColors.lime, NFColors.limeInk),
  accent(NFColors.accent, NFColors.accentInk),
  warn(NFColors.warn, Color(0xFFFFFFFF)),
  violet(Color(0xFF7A4BFF), Color(0xFFFFFFFF)),
  teal(Color(0xFF1EC6B6), Color(0xFFFFFFFF)),
  rose(Color(0xFFFF7A8A), Color(0xFFFFFFFF)),
  ink(NFColors.ink, Color(0xFFFFFFFF));

  const CollectionTone(this.bg, this.fg);

  final Color bg;
  final Color fg;
}

/// Bottom sheet for picking an existing space or creating a new one.
///
/// Mirrors `AddToSpaceSheet` in `design/reference/mockup/cards.jsx`. Parents
/// own the open/close state — render only when `sourceTitle` is ready.
class AddToSpaceSheet extends StatelessWidget {
  const AddToSpaceSheet({
    super.key,
    required this.sourceTitle,
    required this.collections,
    required this.onClose,
    required this.onCreate,
    required this.onSelect,
  });

  /// Display name of the source being filed (appears in the title block).
  final String sourceTitle;

  /// User-owned collections rendered as rows below the create-tile.
  final List<UserCollection> collections;

  /// Tap on scrim or «Отмена» row.
  final VoidCallback onClose;

  /// Tap on the dashed «Создать новое пространство» tile.
  final VoidCallback onCreate;

  /// Tap on an existing-collection row — receives the collection id.
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    return SheetBase(
      onClose: onClose,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.8,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              _TitleBlock(sourceTitle: sourceTitle),
              _CreateTile(onTap: onCreate),
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 6),
                child: NFText.mono(
                  'ВАШИ ПРОСТРАНСТВА · ${collections.length}',
                ),
              ),
              if (collections.isEmpty)
                Container(
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: NFColors.surface2,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  alignment: Alignment.center,
                  child: const Text(
                    'У вас пока нет пространств. Создайте новое выше.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 13,
                      color: NFColors.mute,
                    ),
                  ),
                ),
              for (final UserCollection c in collections)
                _CollectionRow(
                  collection: c,
                  onTap: () => onSelect(c.id),
                ),
              const SizedBox(height: 8),
              _CancelRow(onTap: onClose),
            ],
          ),
        ),
      ),
    );
  }
}

class _TitleBlock extends StatelessWidget {
  const _TitleBlock({required this.sourceTitle});

  final String sourceTitle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 0, 8, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const NFText.mono('ДОБАВИТЬ В ПРОСТРАНСТВО'),
          const SizedBox(height: 4),
          Text(
            'Куда сохранить источник\n«$sourceTitle»?',
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 20,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.5,
              color: NFColors.ink,
              height: 1.15,
            ),
          ),
        ],
      ),
    );
  }
}

class _CreateTile extends StatelessWidget {
  const _CreateTile({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 6, 4, 10),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: _DashedBorder(
          radius: 14,
          color: NFColors.ink,
          strokeWidth: 1.5,
          child: Container(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: <Widget>[
                Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: NFColors.lime,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  alignment: Alignment.center,
                  child: const NFIcon(
                    'plus',
                    size: 18,
                    color: NFColors.limeInk,
                  ),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Text(
                        'Создать новое пространство',
                        style: TextStyle(
                          fontFamily: 'Nunito',
                          fontWeight: FontWeight.w700,
                          fontSize: 15,
                          color: NFColors.ink,
                        ),
                      ),
                      SizedBox(height: 2),
                      Text(
                        'Источник будет добавлен сразу',
                        style: TextStyle(
                          fontFamily: 'Nunito',
                          fontSize: 12.5,
                          color: NFColors.mute,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                const NFIcon('chevron', size: 14, color: NFColors.mute),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CollectionRow extends StatelessWidget {
  const _CollectionRow({required this.collection, required this.onTap});

  final UserCollection collection;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: NFColors.surface2,
            borderRadius: BorderRadius.circular(14),
          ),
          child: Row(
            children: <Widget>[
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: collection.tone.bg,
                  borderRadius: BorderRadius.circular(10),
                ),
                alignment: Alignment.center,
                child: NFIcon(
                  'layers',
                  size: 17,
                  color: collection.tone.fg,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  collection.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontWeight: FontWeight.w700,
                    fontSize: 14.5,
                    color: NFColors.ink,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              const NFIcon('plus', size: 16, color: NFColors.mute),
            ],
          ),
        ),
      ),
    );
  }
}

class _CancelRow extends StatelessWidget {
  const _CancelRow({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: NFColors.surface2,
          borderRadius: BorderRadius.circular(14),
        ),
        alignment: Alignment.center,
        child: const Text(
          'Отмена',
          style: TextStyle(
            fontFamily: 'Nunito',
            fontWeight: FontWeight.w600,
            fontSize: 14,
            color: NFColors.ink,
          ),
        ),
      ),
    );
  }
}

/// Dashed rounded-rectangle border painter — used by the «Создать новое
/// пространство» tile. Pattern: 6-px dash + 4-px gap, `strokeWidth` and
/// `color` configurable.
class _DashedBorder extends StatelessWidget {
  const _DashedBorder({
    required this.child,
    required this.radius,
    required this.color,
    required this.strokeWidth,
  });

  final Widget child;
  final double radius;
  final Color color;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _DashedPainter(
        color: color,
        radius: radius,
        strokeWidth: strokeWidth,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: child,
      ),
    );
  }
}

class _DashedPainter extends CustomPainter {
  _DashedPainter({
    required this.color,
    required this.radius,
    required this.strokeWidth,
  });

  final Color color;
  final double radius;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final Paint paint = Paint()
      ..color = color
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke;
    final RRect rrect = RRect.fromRectAndRadius(
      Offset.zero & size,
      Radius.circular(radius),
    );
    final Path path = Path()..addRRect(rrect);
    const double dash = 6;
    const double gap = 4;
    for (final PathMetric metric in path.computeMetrics()) {
      double distance = 0;
      while (distance < metric.length) {
        final double next = distance + dash;
        final Path segment = metric.extractPath(
          distance,
          next.clamp(0, metric.length),
        );
        canvas.drawPath(segment, paint);
        distance = next + gap;
      }
    }
  }

  @override
  bool shouldRepaint(covariant _DashedPainter old) {
    return old.color != color ||
        old.radius != radius ||
        old.strokeWidth != strokeWidth;
  }
}
