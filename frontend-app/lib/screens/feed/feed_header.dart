import 'package:flutter/widgets.dart';

import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../theme/colors.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';

/// Feed screen header — logo-mark + «Радар» wordmark on the left,
/// «Для вас» accent pill on the right.
///
/// Mirrors `FeedHeader` in `design/reference/mockup/screens.jsx`. Padding is
/// breakpoint-adaptive:
/// * mobile  — `EdgeInsets.fromLTRB(18, 62, 18, 14)` (62 px covers the
///   `DeviceFrame` status bar).
/// * tablet / desktop — `EdgeInsets.fromLTRB(32, 20, 32, 14)` (spec P16 §
///   FeedHeader).
///
/// Background is `NFColors.bg` so the header tucks in with the app surface
/// on both shell layouts.
class FeedHeader extends StatelessWidget {
  const FeedHeader({super.key});

  @override
  Widget build(BuildContext context) {
    final Breakpoint bp = context.breakpoint;
    // Mobile: 18-px top pad on top of the real safe-area top inset.
    // Previously the header reserved 62 px hard — that was to clear the
    // `DeviceFrame` status bar stub. With real browsers, SafeArea handles
    // notches and we only need breathing room above the logo.
    final EdgeInsets padding = bp == Breakpoint.mobile
        ? const EdgeInsets.fromLTRB(18, 18, 18, 14)
        : const EdgeInsets.fromLTRB(32, 20, 32, 14);
    return ColoredBox(
      color: NFColors.bg,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: padding,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: const <Widget>[
              _LogoMark(),
              SizedBox(width: 10),
              NFText.h2('Радар'),
              Spacer(),
              _ForYouPill(),
            ],
          ),
        ),
      ),
    );
  }
}

class _LogoMark extends StatelessWidget {
  const _LogoMark();

  @override
  Widget build(BuildContext context) {
    return const NFIcon('radar', size: 28);
  }
}

class _ForYouPill extends StatelessWidget {
  const _ForYouPill();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: const BoxDecoration(
        color: NFColors.ink,
        borderRadius: BorderRadius.all(Radius.circular(999)),
      ),
      child: const Text(
        'Для вас',
        style: TextStyle(
          fontFamily: 'Nunito',
          fontSize: 12.5,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.2,
          color: NFColors.accentInk,
          height: 1.0,
        ),
      ),
    );
  }
}
