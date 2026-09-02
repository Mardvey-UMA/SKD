// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'device_info_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

String _$deviceInfoRepositoryHash() =>
    r'3a93fff2381cfd9a6cc40571d0b3f8231b99da12';

/// Provides the [IDeviceInfoRepository] implementation.
///
/// This is the dependency injection point where the mock can be swapped
/// for a real implementation. Currently uses [DeviceInfoRepositoryImpl].
///
/// Copied from [deviceInfoRepository].
@ProviderFor(deviceInfoRepository)
final deviceInfoRepositoryProvider =
    AutoDisposeProvider<IDeviceInfoRepository>.internal(
      deviceInfoRepository,
      name: r'deviceInfoRepositoryProvider',
      debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
          ? null
          : _$deviceInfoRepositoryHash,
      dependencies: null,
      allTransitiveDependencies: null,
    );

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
typedef DeviceInfoRepositoryRef = AutoDisposeProviderRef<IDeviceInfoRepository>;
String _$deviceInfoNotifierHash() =>
    r'8d49c2db19279954bbb420309bc106791171b268';

/// Notifier that manages the async loading of device information.
///
/// Uses [AsyncNotifier] to handle loading, data, and error states.
/// The [build] method is called automatically when the provider is first watched.
///
/// Copied from [DeviceInfoNotifier].
@ProviderFor(DeviceInfoNotifier)
final deviceInfoNotifierProvider =
    AutoDisposeAsyncNotifierProvider<DeviceInfoNotifier, DeviceInfo>.internal(
      DeviceInfoNotifier.new,
      name: r'deviceInfoNotifierProvider',
      debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
          ? null
          : _$deviceInfoNotifierHash,
      dependencies: null,
      allTransitiveDependencies: null,
    );

typedef _$DeviceInfoNotifier = AutoDisposeAsyncNotifier<DeviceInfo>;
// ignore_for_file: type=lint
// ignore_for_file: subtype_of_sealed_class, invalid_use_of_internal_member, invalid_use_of_visible_for_testing_member, deprecated_member_use_from_same_package
