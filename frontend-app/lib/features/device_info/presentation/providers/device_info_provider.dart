import 'package:device_info_plus/device_info_plus.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../data/repositories/device_info_repository_impl.dart';
import '../../domain/entities/device_info.dart';
import '../../domain/repositories/i_device_info_repository.dart';

part 'device_info_provider.g.dart';

/// Provides the [IDeviceInfoRepository] implementation.
///
/// This is the dependency injection point where the mock can be swapped
/// for a real implementation. Currently uses [DeviceInfoRepositoryImpl].
@riverpod
IDeviceInfoRepository deviceInfoRepository(DeviceInfoRepositoryRef ref) {
  return DeviceInfoRepositoryImpl(DeviceInfoPlugin());
}

/// Notifier that manages the async loading of device information.
///
/// Uses [AsyncNotifier] to handle loading, data, and error states.
/// The [build] method is called automatically when the provider is first watched.
@riverpod
class DeviceInfoNotifier extends _$DeviceInfoNotifier {
  @override
  Future<DeviceInfo> build() async {
    final repository = ref.watch(deviceInfoRepositoryProvider);
    return repository.getDeviceInfo();
  }
}
