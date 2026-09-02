import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import 'nf_icon.dart';
import 'nf_text.dart';

/// Source chip — shows the publishing platform's 18×18 ink tile with a
/// rotated lime diamond glyph, the source name, a mono timestamp, optional
/// mono read-time, and an optional 3-dot `more` affordance on the right.
///
/// Mirrors `SourceLine` in `design/reference/mockup/cards.jsx`. The diamond
/// glyph is a placeholder — favicons are NOT fetched (see Prompt 07 `Do NOT`).
///
/// When the source name is long, the row wraps via [Wrap] so none of the
/// children overflow their container.
class SourceLine extends StatelessWidget {
  const SourceLine({
    super.key,
    required this.source,
    required this.time,
    this.readTime,
    this.onMore,
  });

  /// Source display name (e.g. `Хабр`, `VC.RU`).
  final String source;

  /// Absolute timestamp the card refers to. Rendered as a short relative
  /// label (`5м`, `3ч`, `2д`) via [_formatRelative].
  final DateTime time;

  /// Pre-formatted read-time label (e.g. `2 мин чтения`). Optional — when
  /// `null` the second dot-separator + label are omitted entirely.
  final String? readTime;

  /// Invoked on tap of the 28×28 `more` button. When `null` the button is
  /// not rendered at all.
  final VoidCallback? onMore;

  @override
  Widget build(BuildContext context) {
    final List<Widget> leading = <Widget>[
      const _SourceGlyph(),
      Text(
        source,
        style: const TextStyle(
          fontFamily: 'Nunito',
          fontSize: 13,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.1,
          color: NFColors.ink,
        ),
      ),
      const _DotSeparator(),
      NFText.mono(_formatRelative(time)),
    ];

    if (readTime != null) {
      leading.add(const _DotSeparator());
      leading.add(NFText.mono(readTime!));
    }

    final Widget metaWrap = Wrap(
      spacing: 8,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: leading,
    );

    if (onMore == null) {
      return metaWrap;
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        Expanded(child: metaWrap),
        _MoreButton(onTap: onMore!),
      ],
    );
  }

  static String _formatRelative(DateTime time) {
    final Duration diff = DateTime.now().difference(time);
    if (diff.inMinutes < 1) return 'сейчас';
    if (diff.inMinutes < 60) return '${diff.inMinutes}м';
    if (diff.inHours < 24) return '${diff.inHours}ч';
    return '${diff.inDays}д';
  }
}

class _SourceGlyph extends StatelessWidget {
  const _SourceGlyph();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 18,
      height: 18,
      decoration: BoxDecoration(
        color: NFColors.ink,
        borderRadius: BorderRadius.circular(4),
      ),
      alignment: Alignment.center,
      child: const RotatedBox(
        quarterTurns: 1,
        child: ColoredBox(
          color: NFColors.lime,
          child: SizedBox(width: 10, height: 10),
        ),
      ),
    );
  }
}

class _DotSeparator extends StatelessWidget {
  const _DotSeparator();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 3,
      height: 3,
      decoration: const BoxDecoration(
        color: NFColors.mute2,
        shape: BoxShape.circle,
      ),
    );
  }
}

class _MoreButton extends StatelessWidget {
  const _MoreButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Ещё',
      button: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: const SizedBox(
          width: 28,
          height: 28,
          child: Center(
            child: NFIcon('more', size: 16, color: NFColors.mute),
          ),
        ),
      ),
    );
  }
}
