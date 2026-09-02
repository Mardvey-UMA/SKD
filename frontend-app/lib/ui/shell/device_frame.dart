import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'devices/galaxy.dart';
import 'devices/ios.dart';
import 'devices/pixel.dart';

/// Enumerates the device bezel variants supported by [DeviceFrame].
enum DeviceKind {
  /// iPhone shell — dynamic island + home indicator. Port of
  /// `design/reference/mockup/ios-frame.jsx` § IOSDevice.
  iphone,

  /// Pixel shell — centred 12-px punch-hole. Port of
  /// `design/reference/mockup/device-frame.jsx` § PixelDevice.
  pixel,

  /// Galaxy shell — slightly smaller + higher punch-hole, thicker inky
  /// border. Port of `device-frame.jsx` § GalaxyDevice.
  galaxy,
}

/// Unified device-frame wrapper used by [ResponsiveShell] on mobile.
///
/// In release builds the bezel is elided entirely and [child] is returned
/// verbatim — no chrome, no assets — so device-frame primitives never ship
/// in the production bundle (spec acceptance criterion: «`build/web` нет
/// device-frame assets»).
///
/// In debug / profile builds the frame switches on [kind]:
///
/// * [DeviceKind.iphone]  → [IOSDevice]
/// * [DeviceKind.pixel]   → [PixelDevice]
/// * [DeviceKind.galaxy]  → [GalaxyDevice]
///
/// The [releaseOverride] constructor parameter exists **only** for tests —
/// runtime code should never set it. When non-null it takes precedence over
/// [kReleaseMode], letting `device_frame_test.dart` assert both branches.
class DeviceFrame extends StatelessWidget {
  const DeviceFrame({
    super.key,
    required this.child,
    this.kind = DeviceKind.iphone,
    this.width = 402,
    this.height = 874,
    @visibleForTesting this.releaseOverride,
  });

  final Widget child;
  final DeviceKind kind;
  final double width;
  final double height;

  /// Test-only override for [kReleaseMode] release-guard behaviour.
  @visibleForTesting
  final bool? releaseOverride;

  @override
  Widget build(BuildContext context) {
    final bool isRelease = releaseOverride ?? kReleaseMode;
    if (isRelease) {
      return child;
    }
    switch (kind) {
      case DeviceKind.iphone:
        return IOSDevice(width: width, height: height, child: child);
      case DeviceKind.pixel:
        return PixelDevice(width: width, height: height, child: child);
      case DeviceKind.galaxy:
        return GalaxyDevice(width: width, height: height, child: child);
    }
  }
}
