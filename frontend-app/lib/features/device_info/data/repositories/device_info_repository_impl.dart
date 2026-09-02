import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../domain/entities/device_info.dart';
import '../../domain/repositories/i_device_info_repository.dart';
import '../models/device_info_dto.dart';

/// Implementation of [IDeviceInfoRepository] using device_info_plus.
///
/// Fetches device information from the platform-specific plugins
/// and maps them to the domain entity using [DeviceInfoDto].
class DeviceInfoRepositoryImpl implements IDeviceInfoRepository {
  final DeviceInfoPlugin _deviceInfoPlugin;

  DeviceInfoRepositoryImpl(this._deviceInfoPlugin);

  @override
  Future<DeviceInfo> getDeviceInfo() async {
    final packageInfo = await PackageInfo.fromPlatform();

    if (Platform.isAndroid) {
      final androidInfo = await _deviceInfoPlugin.androidInfo;
      return DeviceInfoDto.fromAndroid(androidInfo, packageInfo);
    } else if (Platform.isIOS) {
      final iosInfo = await _deviceInfoPlugin.iosInfo;
      return DeviceInfoDto.fromIos(iosInfo, packageInfo);
    } else {
      throw UnsupportedError('Unsupported platform');
    }
  }
}
