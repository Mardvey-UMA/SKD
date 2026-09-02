import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import '../../theme/motion.dart';
import '../motion/press_scale.dart';
import 'nf_icon.dart';

/// Reaction bar atom — three circular buttons (Like / Dislike / Bookmark).
///
/// Mirrors `ReactionBar` in `design/reference/mockup/cards.jsx`. Strictly
/// stateless: the parent owns mutual-exclusion logic between Like / Dislike
/// (e.g. tapping Like while Dislike is active does NOT flip Dislike here).
///
/// * Default buttons are 40×40 (compact 34×34), gap `8`.
/// * Active visuals per button:
///   * Like — fill [NFColors.accent], icon `thumb-up` in [NFColors.accentInk].
///   * Dislike — fill [NFColors.ink], icon `thumb-down` in white.
///   * Bookmark — fill [NFColors.lime], icon `bookmark` in [NFColors.limeInk].
/// * Inactive — transparent fill + hairline border + icon tint [NFColors.ink2].
/// * Fill / border transitions animate over [NFMotion.fastDuration] (180 ms).
/// * Press-down scales the tapped button to `0.92` via the shared
///   [PressScale] micro-interaction (same 180 ms timing).
/// * Taps do NOT bubble through ([HitTestBehavior.opaque]) — so tapping a
///   reaction inside a card does not trigger the card's `onOpen`.
class ReactionBar extends StatelessWidget {
  const ReactionBar({
    super.key,
    required this.isLiked,
    required this.isDisliked,
    required this.isBookmarked,
    required this.onLike,
    required this.onDislike,
    required this.onBookmark,
    this.compact = false,
  });

  final bool isLiked;
  final bool isDisliked;
  final bool isBookmarked;

  final VoidCallback onLike;
  final VoidCallback onDislike;
  final VoidCallback onBookmark;

  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        _ReactionButton(
          iconName: 'thumb-up',
          active: isLiked,
          activeFill: NFColors.accent,
          activeIconColor: NFColors.accentInk,
          semanticLabel: 'Нравится',
          compact: compact,
          onTap: onLike,
        ),
        const SizedBox(width: 8),
        _ReactionButton(
          iconName: 'thumb-down',
          active: isDisliked,
          activeFill: NFColors.ink,
          activeIconColor: const Color(0xFFFFFFFF),
          semanticLabel: 'Не нравится',
          compact: compact,
          onTap: onDislike,
        ),
        const SizedBox(width: 8),
        _ReactionButton(
          iconName: 'bookmark',
          active: isBookmarked,
          activeFill: NFColors.lime,
          activeIconColor: NFColors.limeInk,
          semanticLabel: 'В закладки',
          compact: compact,
          onTap: onBookmark,
        ),
      ],
    );
  }
}

class _ReactionButton extends StatelessWidget {
  const _ReactionButton({
    required this.iconName,
    required this.active,
    required this.activeFill,
    required this.activeIconColor,
    required this.semanticLabel,
    required this.compact,
    required this.onTap,
  });

  final String iconName;
  final bool active;
  final Color activeFill;
  final Color activeIconColor;
  final String semanticLabel;
  final bool compact;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final double size = compact ? 34 : 40;
    final Color fill = active ? activeFill : const Color(0x00000000);
    final Color borderColor = active ? activeFill : NFColors.hairline;
    final Color iconColor = active ? activeIconColor : NFColors.ink2;

    return Semantics(
      label: semanticLabel,
      button: true,
      child: PressScale(
        onTap: onTap,
        duration: NFMotion.fastDuration,
        child: AnimatedContainer(
          duration: NFMotion.fastDuration,
          curve: NFMotion.fastCurve,
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: fill,
            shape: BoxShape.circle,
            border: Border.all(color: borderColor, width: 1),
          ),
          alignment: Alignment.center,
          child: NFIcon(
            iconName,
            size: 16,
            color: iconColor,
          ),
        ),
      ),
    );
  }
}
