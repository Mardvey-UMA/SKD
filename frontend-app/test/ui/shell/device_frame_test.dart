import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/shell/device_frame.dart';
import 'package:frontend_app/ui/shell/devices/_android_common.dart';
import 'package:frontend_app/ui/shell/devices/galaxy.dart';
import 'package:frontend_app/ui/shell/devices/ios.dart';
import 'package:frontend_app/ui/shell/devices/pixel.dart';

Future<void> pumpFrame(WidgetTester tester, Widget frame) async {
  await tester.pumpWidget(
    Directionality(
      textDirection: TextDirection.ltr,
      child: MediaQuery(
        data: const MediaQueryData(size: Size(1000, 1200)),
        child: Center(child: frame),
      ),
    ),
  );
}

void main() {
  const Key childKey = Key('device-frame-child');
  const Widget sentinel = SizedBox.expand(key: childKey);

  group('DeviceFrame · debug-mode bezel rendering', () {
    testWidgets('DeviceKind.iphone renders IOSDevice + child', (
      WidgetTester tester,
    ) async {
      await pumpFrame(
        tester,
        const DeviceFrame(
          kind: DeviceKind.iphone,
          releaseOverride: false,
          child: sentinel,
        ),
      );

      expect(find.byType(IOSDevice), findsOneWidget);
      expect(find.byType(PixelDevice), findsNothing);
      expect(find.byType(GalaxyDevice), findsNothing);
      expect(find.byKey(childKey), findsOneWidget);
    });

    testWidgets('DeviceKind.pixel renders PixelDevice + AndroidStatus + bar', (
      WidgetTester tester,
    ) async {
      await pumpFrame(
        tester,
        const DeviceFrame(
          kind: DeviceKind.pixel,
          releaseOverride: false,
          child: sentinel,
        ),
      );

      expect(find.byType(PixelDevice), findsOneWidget);
      expect(find.byType(IOSDevice), findsNothing);
      expect(find.byType(GalaxyDevice), findsNothing);
      expect(find.byType(AndroidStatus), findsOneWidget);
      expect(find.byType(AndroidGestureBar), findsOneWidget);
      expect(find.byKey(childKey), findsOneWidget);
    });

    testWidgets('DeviceKind.galaxy renders GalaxyDevice + AndroidStatus + bar',
        (WidgetTester tester) async {
      await pumpFrame(
        tester,
        const DeviceFrame(
          kind: DeviceKind.galaxy,
          releaseOverride: false,
          child: sentinel,
        ),
      );

      expect(find.byType(GalaxyDevice), findsOneWidget);
      expect(find.byType(IOSDevice), findsNothing);
      expect(find.byType(PixelDevice), findsNothing);
      expect(find.byType(AndroidStatus), findsOneWidget);
      expect(find.byType(AndroidGestureBar), findsOneWidget);
      expect(find.byKey(childKey), findsOneWidget);
    });
  });

  group('DeviceFrame · release-mode guard', () {
    testWidgets(
      'releaseOverride=true returns child only — no bezel assets rendered',
      (WidgetTester tester) async {
        await pumpFrame(
          tester,
          const DeviceFrame(
            kind: DeviceKind.iphone,
            releaseOverride: true,
            child: sentinel,
          ),
        );

        expect(find.byKey(childKey), findsOneWidget);
        expect(find.byType(IOSDevice), findsNothing);
        expect(find.byType(PixelDevice), findsNothing);
        expect(find.byType(GalaxyDevice), findsNothing);
        expect(find.byType(AndroidStatus), findsNothing);
        expect(find.byType(AndroidGestureBar), findsNothing);
      },
    );

    testWidgets(
      'releaseOverride=true elides bezel for every DeviceKind',
      (WidgetTester tester) async {
        for (final DeviceKind kind in DeviceKind.values) {
          await pumpFrame(
            tester,
            DeviceFrame(
              kind: kind,
              releaseOverride: true,
              child: const SizedBox.expand(key: childKey),
            ),
          );

          expect(find.byKey(childKey), findsOneWidget);
          expect(find.byType(IOSDevice), findsNothing);
          expect(find.byType(PixelDevice), findsNothing);
          expect(find.byType(GalaxyDevice), findsNothing);
        }
      },
    );
  });
}
