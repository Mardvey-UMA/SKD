/// Entity representing device information.
///
/// This is an immutable class containing all device-related data
/// that will be displayed to the user.
class DeviceInfo {
  /// The name of the device (e.g., "Pixel 9")
  final String deviceName;

  /// The model identifier (e.g., "sdk_gphone64_x86_64")
  final String model;

  /// The operating system version (e.g., "Android 14")
  final String osVersion;

  /// The platform name (e.g., "Android", "iOS")
  final String platform;

  /// The Android SDK version (e.g., 34 for Android 14)
  final int sdkVersion;

  /// The application version from pubspec.yaml
  final String appVersion;

  /// Creates a new [DeviceInfo] instance.
  const DeviceInfo({
    required this.deviceName,
    required this.model,
    required this.osVersion,
    required this.platform,
    required this.sdkVersion,
    required this.appVersion,
  });
}
