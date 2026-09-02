import 'package:flutter/painting.dart';

import '../../../theme/colors.dart';

/// Visual style for a backend-owned onboarding category.
///
/// The backend (`GET /api/users/me/categories`) is the single source of
/// truth for WHICH categories the user can pick and WHAT they are called
/// (Russian `name`, canonical `id` — see API_CONTRACTS.md §2.3). The
/// client owns only the chip presentation — emoji + chip fill colours.
///
/// Unknown / newly-added backend categories fall back to [fallbackStyle]
/// so the UI never crashes if the ontology grows on the server.
class InterestStyle {
  const InterestStyle({
    required this.emoji,
    required this.background,
    required this.foreground,
  });

  /// Emoji glyph shown left of the label.
  final String emoji;

  /// Selected-state fill.
  final Color background;

  /// Selected-state text colour on top of [background].
  final Color foreground;
}

/// Fallback style for categories whose `id` is not in
/// [kInterestStylesById]. Neutral surface-2 + ink keeps chips readable.
const InterestStyle fallbackStyle = InterestStyle(
  emoji: '🏷️',
  background: NFColors.surface2,
  foreground: NFColors.ink,
);

/// Maps the 18 canonical backend category ids to their chip style.
///
/// Ids are lowercase Russian strings as returned by
/// `GET /api/users/me/categories`. Entries are intentionally hand-picked
/// — the rec-system ontology is deliberately small and stable, so an
/// explicit table beats a runtime heuristic on the emoji / colour axes.
const Map<String, InterestStyle> kInterestStylesById = <String, InterestStyle>{
  'технологии': InterestStyle(
    emoji: '💻',
    background: NFColors.accent,
    foreground: NFColors.accentInk,
  ),
  'спорт': InterestStyle(
    emoji: '🏃',
    background: Color(0xFFFFC9C2),
    foreground: NFColors.ink,
  ),
  'наука': InterestStyle(
    emoji: '🔬',
    background: Color(0xFFC4ECE7),
    foreground: NFColors.ink,
  ),
  'политика': InterestStyle(
    emoji: '🏛️',
    background: NFColors.surface2,
    foreground: NFColors.ink,
  ),
  'экономика': InterestStyle(
    emoji: '📈',
    background: Color(0xFFD7F0A3),
    foreground: NFColors.ink,
  ),
  'культура': InterestStyle(
    emoji: '🎭',
    background: Color(0xFFF6C3D1),
    foreground: NFColors.ink,
  ),
  'общество': InterestStyle(
    emoji: '👥',
    background: Color(0xFFE0D5FF),
    foreground: NFColors.ink,
  ),
  'бизнес': InterestStyle(
    emoji: '💼',
    background: Color(0xFFE0D5FF),
    foreground: NFColors.ink,
  ),
  'финансы': InterestStyle(
    emoji: '💶',
    background: Color(0xFFD7F0A3),
    foreground: NFColors.ink,
  ),
  'здоровье': InterestStyle(
    emoji: '❤️',
    background: Color(0xFFFFC9C2),
    foreground: NFColors.ink,
  ),
  'развлечения': InterestStyle(
    emoji: '🎬',
    background: Color(0xFFF6C3D1),
    foreground: NFColors.ink,
  ),
  'образование': InterestStyle(
    emoji: '🎓',
    background: Color(0xFFC9E3FF),
    foreground: NFColors.ink,
  ),
  'международные новости': InterestStyle(
    emoji: '🌍',
    background: Color(0xFFC9E3FF),
    foreground: NFColors.ink,
  ),
  'происшествия': InterestStyle(
    emoji: '⚠️',
    background: Color(0xFFFFE08C),
    foreground: NFColors.ink,
  ),
  'криминал': InterestStyle(
    emoji: '⚖️',
    background: NFColors.ink,
    foreground: NFColors.accentInk,
  ),
  'армия': InterestStyle(
    emoji: '🛡️',
    background: NFColors.surface2,
    foreground: NFColors.ink,
  ),
  'природа': InterestStyle(
    emoji: '🌱',
    background: Color(0xFFCFF2D8),
    foreground: NFColors.ink,
  ),
  'транспорт': InterestStyle(
    emoji: '🚗',
    background: Color(0xFFFFD2B8),
    foreground: NFColors.ink,
  ),
};

/// Resolves a chip style for a backend category id, falling back to
/// [fallbackStyle] when the id is unknown to the client.
InterestStyle resolveInterestStyle(String id) =>
    kInterestStylesById[id.toLowerCase()] ?? fallbackStyle;
