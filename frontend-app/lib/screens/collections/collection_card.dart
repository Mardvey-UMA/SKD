import 'package:characters/characters.dart';
import 'package:flutter/material.dart';

import '../../theme/colors.dart';
import '../../ui/atoms/hatched_painter.dart';
import '../../ui/atoms/nf_text.dart';
import '../../ui/atoms/stripe_placeholder.dart';

/// User-collection card rendered on the redesigned Collections screen.
///
/// Two visual variants, controlled by [showStats]:
/// * `true`  — big count number bottom-right + `{count} МАТЕРИАЛОВ · {sources}
///   ИСТОЧНИКОВ` caption. Used for system tiles (Saved / Liked / Disliked)
///   where the count is meaningful.
/// * `false` — 2-letter initials badge on the header block, no caption.
///   Used for user spaces where per-space material counts aren't tracked.
class CollectionCard extends StatelessWidget {
  const CollectionCard({
    super.key,
    required this.title,
    required this.tone,
    this.count = 0,
    this.sources = 0,
    this.showStats = true,
    this.onTap,
  });

  final String title;
  final int count;
  final int sources;
  final StripeTone tone;
  final bool showStats;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final Color textOnHeader = _isDarkTone(tone)
        ? NFColors.accentInk
        : NFColors.ink;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: NFColors.hairline),
        ),
        clipBehavior: Clip.hardEdge,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.all(8),
              child: _HeaderBlock(
                tone: tone,
                label: showStats ? '$count' : _initialsOf(title),
                labelIsCount: showStats,
                textColor: textOnHeader,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 4, 14, 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.4,
                      color: NFColors.ink,
                      height: 1.15,
                    ),
                  ),
                  if (showStats) ...<Widget>[
                    const SizedBox(height: 6),
                    NFText.mono(
                        '$count МАТЕРИАЛОВ · $sources ИСТОЧНИКОВ'),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  static bool _isDarkTone(StripeTone t) {
    switch (t) {
      case StripeTone.ink:
      case StripeTone.accent:
      case StripeTone.violet:
        return true;
      case StripeTone.lime:
      case StripeTone.warn:
      case StripeTone.teal:
      case StripeTone.rose:
      case StripeTone.light:
        return false;
    }
  }
}

class _HeaderBlock extends StatelessWidget {
  const _HeaderBlock({
    required this.tone,
    required this.label,
    required this.labelIsCount,
    required this.textColor,
  });

  final StripeTone tone;
  final String label;
  final bool labelIsCount;
  final Color textColor;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        height: 120,
        width: double.infinity,
        child: Stack(
          fit: StackFit.expand,
          children: <Widget>[
            ColoredBox(color: tone.primary),
            CustomPaint(
              painter: HatchedPainter(
                period: 14,
                strokeWidth: 2,
                color: const Color.fromRGBO(14, 15, 13, 0.18),
              ),
            ),
            Positioned(
              right: 14,
              bottom: 10,
              child: Text(
                label,
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: labelIsCount ? 48 : 40,
                  fontWeight: FontWeight.w800,
                  letterSpacing: labelIsCount ? -1.6 : -1.2,
                  height: 1,
                  color: textColor,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

String _initialsOf(String name) {
  final String trimmed = name.trim();
  if (trimmed.isEmpty) return '•';
  final List<String> words = trimmed
      .split(RegExp(r'\s+'))
      .where((w) => w.isNotEmpty)
      .toList(growable: false);
  if (words.length >= 2) {
    return (words[0].characters.first + words[1].characters.first)
        .toUpperCase();
  }
  final String first = words.first;
  return first.characters.take(2).toString().toUpperCase();
}
