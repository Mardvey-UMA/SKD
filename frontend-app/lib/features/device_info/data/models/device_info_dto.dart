import '../../domain/entities/device_info.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';

/// Data Transfer Object for mapping platform-specific device data.
///
/// Contains static mapper methods to convert plugin data to domain entity.
class DeviceInfoDto {
  /// Maps Android device info and package info to DeviceInfo entity.
  static DeviceInfo fromAndroid(
    AndroidDeviceInfo androidInfo,
    PackageInfo packageInfo,
  ) {
    return DeviceInfo(
      deviceName: androidInfo.device,
      model: androidInfo.model,
      osVersion: 'Android ${androidInfo.version.release}',
      platform: 'Android',
      sdkVersion: androidInfo.version.sdkInt,
      appVersion: packageInfo.version,
    );
  }

  /// Maps iOS device info and package info to DeviceInfo entity.
  static DeviceInfo fromIos(IosDeviceInfo iosInfo, PackageInfo packageInfo) {
    return DeviceInfo(
      deviceName: iosInfo.name,
      model: iosInfo.model,
      osVersion: 'iOS ${iosInfo.systemVersion}',
      platform: 'iOS',
      sdkVersion: 0, // iOS does not have SDK version concept
      appVersion: packageInfo.version,
    );
  }
}
