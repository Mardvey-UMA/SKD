import 'package:flutter/widgets.dart';

import '../../theme/colors.dart';
import 'nf_icon.dart';

/// Icon button atom — 38×38 surface circle with hairline border and a
/// centered 17-px [NFIcon]. When [warnDot] is `true`, a 7×7 warn-colored
/// badge with a 1.5-px surface ring is layered in the top-right.
///
/// Mirrors `IconBtn` in `design/reference/mockup/cards.jsx`. Stateless and
/// tap-opaque — parents own any mutation (toggles, counters).
class IconBtn extends StatelessWidget {
  const IconBtn({
    super.key,
    required this.iconName,
    required this.onTap,
    this.warnDot = false,
    this.semanticLabel,
  });

  /// Name of the icon under `assets/icons/{name}.svg`.
  final String iconName;

  /// Tap callback. Always required — idle icon buttons have no purpose.
  final VoidCallback onTap;

  /// When `true`, renders a 7×7 orange badge in the top-right corner with
  /// a 1.5-px [NFColors.surface] ring.
  final bool warnDot;

  /// Semantic label for accessibility. Defaults to [iconName] when `null`.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel ?? iconName,
      button: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: SizedBox(
          width: 38,
          height: 38,
          child: Stack(
            clipBehavior: Clip.none,
            children: <Widget>[
              Positioned.fill(
                child: Container(
                  decoration: BoxDecoration(
                    color: NFColors.surface,
                    shape: BoxShape.circle,
                    border: Border.all(color: NFColors.hairline, width: 1),
                  ),
                  alignment: Alignment.center,
                  child: NFIcon(iconName, size: 17),
                ),
              ),
              if (warnDot)
                const Positioned(
                  top: 7,
                  right: 8,
                  child: _WarnDot(),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _WarnDot extends StatelessWidget {
  const _WarnDot();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 7,
      height: 7,
      decoration: BoxDecoration(
        color: NFColors.warn,
        shape: BoxShape.circle,
        border: Border.all(color: NFColors.surface, width: 1.5),
      ),
    );
  }
}
