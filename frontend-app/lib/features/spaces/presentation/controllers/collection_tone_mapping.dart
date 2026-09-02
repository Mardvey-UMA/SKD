import '../../../../core/sources/domain/space_color.dart';
import '../../../../ui/atoms/stripe_placeholder.dart';

/// Single source of truth for the UI tone ↔ domain `SpaceColor` bridge used
/// by the P18 Collections redesign.
///
/// The UI layer ships 7 swatches (`ink · accent · lime · warn · violet ·
/// teal · rose`). The backend still stores one of the 8 `SpaceColor` enum
/// values. The domain model is **not** renamed (see P18 spec «Do NOT») —
/// this mapping is purely visual glue.
///
/// * `ink`      → `SpaceColor.blue`    — editor default accent
/// * `accent`   → `SpaceColor.purple`  — brand accent
/// * `lime`     → `SpaceColor.green`
/// * `warn`     → `SpaceColor.orange`
/// * `violet`   → `SpaceColor.purple`  (collapsed with accent on read)
/// * `teal`     → `SpaceColor.teal`
/// * `rose`     → `SpaceColor.pink`
///
/// On the reverse direction we map each `SpaceColor` to the closest tone
/// so existing spaces (created before P18) render correctly.
class CollectionToneMapping {
  const CollectionToneMapping._();

  static StripeTone toneFor(SpaceColor color) {
    switch (color) {
      case SpaceColor.red:
        return StripeTone.rose;
      case SpaceColor.orange:
      case SpaceColor.yellow:
        return StripeTone.warn;
      case SpaceColor.green:
        return StripeTone.lime;
      case SpaceColor.teal:
        return StripeTone.teal;
      case SpaceColor.blue:
        return StripeTone.ink;
      case SpaceColor.purple:
        return StripeTone.accent;
      case SpaceColor.pink:
        return StripeTone.rose;
    }
  }

  static SpaceColor colorFor(StripeTone tone) {
    switch (tone) {
      case StripeTone.ink:
        return SpaceColor.blue;
      case StripeTone.accent:
      case StripeTone.violet:
        return SpaceColor.purple;
      case StripeTone.lime:
        return SpaceColor.green;
      case StripeTone.warn:
        return SpaceColor.orange;
      case StripeTone.teal:
        return SpaceColor.teal;
      case StripeTone.rose:
        return SpaceColor.pink;
      case StripeTone.light:
        return SpaceColor.blue;
    }
  }
}
