import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../atoms/nf_icon.dart';
import 'sheet_base.dart';

/// Bottom sheet invoked from a card's three-dot menu.
///
/// Renders two [MenuRow]s — «Добавить источник в пространство» and «Скрыть
/// источник» — plus a `surface2` cancel row. For free-plan users the
/// «add to space» row is `locked` and shows a `PREMIUM` chip. Mirrors
/// `CardMenu` in `design/reference/mockup/cards.jsx`.
///
/// The widget itself is a full-parent overlay ([SheetBase] wraps the scrim
/// + slide animation). Parents own open/close state — render `CardMenu`
/// only when `item` is non-null.
class CardMenu extends StatelessWidget {
  const CardMenu({
    super.key,
    required this.sourceTitle,
    required this.sourceHandle,
    required this.isPremium,
    required this.onClose,
    required this.onAddToSpace,
    required this.onHideSource,
  });

  /// Display name of the source — e.g. «Meduza».
  final String sourceTitle;

  /// Machine handle — uppercase mono line under the title (e.g. `@meduza`).
  final String sourceHandle;

  /// When `false`, the «add to space» row is locked and shows a PREMIUM chip.
  final bool isPremium;

  /// Tap on scrim or «Отмена» row.
  final VoidCallback onClose;

  /// Tap on «Добавить в пространство». Only fires when [isPremium] is true.
  final VoidCallback onAddToSpace;

  /// Tap on «Скрыть источник». Parents typically also close the sheet.
  final VoidCallback onHideSource;

  @override
  Widget build(BuildContext context) {
    return SheetBase(
      onClose: onClose,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          _SourceHeader(title: sourceTitle, handle: sourceHandle),
          const SizedBox(height: 6),
          MenuRow(
            icon: 'folder-plus',
            title: 'Добавить источник в пространство',
            sub: isPremium
                ? 'Выбрать существующее или создать новое'
                : 'Доступно в Premium',
            locked: !isPremium,
            onTap: isPremium ? onAddToSpace : null,
          ),
          MenuRow(
            icon: 'eye-off',
            title: 'Скрыть источник',
            sub: 'Материалы этого источника не будут попадать в ленту',
            danger: true,
            onTap: onHideSource,
          ),
          const SizedBox(height: 8),
          _CancelRow(onTap: onClose),
        ],
      ),
    );
  }
}

class _SourceHeader extends StatelessWidget {
  const _SourceHeader({required this.title, required this.handle});

  final String title;
  final String handle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(color: NFColors.hairline, width: 1),
        ),
      ),
      child: Row(
        children: <Widget>[
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: NFColors.chipBg,
              borderRadius: BorderRadius.circular(10),
            ),
            alignment: Alignment.center,
            child: Transform.rotate(
              angle: 0.7853981633974483,
              child: Container(
                width: 14,
                height: 14,
                decoration: BoxDecoration(
                  color: NFColors.ink,
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontWeight: FontWeight.w700,
                    fontSize: 15,
                    color: NFColors.ink,
                    letterSpacing: -0.2,
                  ),
                ),
                Text(
                  handle.toUpperCase(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.5,
                    color: NFColors.mute,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Single tap-row inside [CardMenu]. Public so tests can locate rows by type.
class MenuRow extends StatelessWidget {
  const MenuRow({
    super.key,
    required this.icon,
    required this.title,
    this.sub,
    this.danger = false,
    this.locked = false,
    this.onTap,
  });

  final String icon;
  final String title;
  final String? sub;
  final bool danger;
  final bool locked;

  /// `null` disables the tap (used when [locked] is `true`).
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final Color iconBg = locked
        ? NFColors.chipBg
        : danger
            ? const Color.fromRGBO(255, 90, 31, 0.10)
            : NFColors.chipBg;
    final Color iconColor = locked
        ? NFColors.mute
        : danger
            ? NFColors.warn
            : NFColors.ink;
    final Color titleColor = danger ? NFColors.warn : NFColors.ink;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: <Widget>[
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: iconBg,
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child: NFIcon(icon, size: 18, color: iconColor),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Flexible(
                        child: Text(
                          title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontFamily: 'Nunito',
                            fontWeight: FontWeight.w600,
                            fontSize: 14.5,
                            color: titleColor,
                          ),
                        ),
                      ),
                      if (locked) ...<Widget>[
                        const SizedBox(width: 6),
                        const _PremiumChip(),
                      ],
                    ],
                  ),
                  if (sub != null) ...<Widget>[
                    const SizedBox(height: 2),
                    Text(
                      sub!,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 12.5,
                        height: 1.4,
                        color: NFColors.mute,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            const NFIcon('chevron', size: 14, color: NFColors.mute),
          ],
        ),
      ),
    );
  }
}

class _PremiumChip extends StatelessWidget {
  const _PremiumChip();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: NFColors.ink,
        borderRadius: BorderRadius.circular(5),
      ),
      child: const Text(
        'PREMIUM',
        style: TextStyle(
          fontFamily: 'Nunito',
          fontSize: 9,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.6,
          color: NFColors.lime,
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
