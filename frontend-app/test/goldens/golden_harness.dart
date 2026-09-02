/// Golden harness — shared helpers for `test/goldens/`.
///
/// Goldens follow the Prompt 25 render settings:
/// * `Brightness.light` + `platformBrightness == light`.
/// * `textScaleFactor = 1.0`.
/// * Three breakpoints per screen: mobile 375×812, tablet 900×1024,
///   desktop 1440×900.
///
/// Animation-driven widgets are rendered at frame 0 via `pump()` with a
/// single `pump(Duration.zero)` — callers handling custom motion may
/// `pump(duration)` explicitly, but most goldens stay deterministic at t=0.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/radar_theme.dart';
import 'package:visibility_detector/visibility_detector.dart';

/// Standard viewport sizes per spec (Prompt 25).
class GoldenBreakpoint {
  const GoldenBreakpoint(this.name, this.size);
  final String name;
  final Size size;

  static const GoldenBreakpoint mobile =
      GoldenBreakpoint('mobile', Size(375, 812));
  static const GoldenBreakpoint tablet =
      GoldenBreakpoint('tablet', Size(900, 1024));
  static const GoldenBreakpoint desktop =
      GoldenBreakpoint('desktop', Size(1440, 900));

  static const List<GoldenBreakpoint> all = <GoldenBreakpoint>[
    mobile,
    tablet,
    desktop,
  ];
}

/// One-time setup for golden tests. Call inside `setUpAll`.
void configureGoldenEnvironment() {
  // VisibilityDetector fires after a 500 ms throttle by default — disable so
  // detectors inside goldens settle within the first `pump`.
  VisibilityDetectorController.instance.updateInterval = Duration.zero;
}

/// Pumps [child] inside a MaterialApp + [RadarTheme.light] harness sized
/// to [breakpoint] with a fixed text-scale factor of 1.0 and
/// `platformBrightness == light`.
Future<void> pumpGolden(
  WidgetTester tester,
  Widget child, {
  required GoldenBreakpoint breakpoint,
  List<Override> overrides = const <Override>[],
}) async {
  tester.view
    ..physicalSize = breakpoint.size
    ..devicePixelRatio = 1.0;
  addTearDown(() {
    tester.view
      ..resetPhysicalSize()
      ..resetDevicePixelRatio();
  });

  await tester.pumpWidget(
    ProviderScope(
      overrides: overrides,
      child: MediaQuery(
        data: MediaQueryData(
          size: breakpoint.size,
          devicePixelRatio: 1.0,
          textScaler: TextScaler.noScaling,
          platformBrightness: Brightness.light,
        ),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: RadarTheme.light(),
          home: child,
        ),
      ),
    ),
  );

  // Let layout settle; animations held at t=0 for deterministic goldens.
  await tester.pump();
}

/// Convenience: runs [body] for each breakpoint in [GoldenBreakpoint.all].
void forEachBreakpoint(void Function(GoldenBreakpoint bp) body) {
  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    body(bp);
  }
}

/// Expects [finder] to match the baseline at `test/goldens/<relativePath>`.
Future<void> expectGolden(Finder finder, String relativePath) async {
  await expectLater(finder, matchesGoldenFile(relativePath));
}
