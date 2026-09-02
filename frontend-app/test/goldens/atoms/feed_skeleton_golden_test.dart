import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/feed_skeleton.dart';

import '../golden_harness.dart';

/// Golden for `FeedSkeleton`. Rendered at frame 0 (shimmer offset = -400 px,
/// pulse eased to 0) so the image is deterministic across runs.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 8.
void main() {
  setUpAll(configureGoldenEnvironment);

  testWidgets('feed_skeleton initial-frame golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const ColoredBox(
        color: NFColors.bg,
        child: TickerMode(
          enabled: false,
          child: SingleChildScrollView(
            padding: EdgeInsets.symmetric(horizontal: 14, vertical: 20),
            child: FeedSkeleton(),
          ),
        ),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(FeedSkeleton),
      'feed_skeleton_initial.png',
    );
  });
}
