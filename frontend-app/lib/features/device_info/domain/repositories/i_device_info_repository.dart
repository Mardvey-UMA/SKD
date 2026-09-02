import '../entities/device_info.dart';

/// Repository interface for fetching device information.
///
/// This interface defines the contract that the Data layer must implement.
/// It is placed in the Domain layer to ensure dependency inversion.
abstract interface class IDeviceInfoRepository {
  /// Fetches device information from the platform.
  ///
  /// Returns a [DeviceInfo] object containing device details.
  /// Throws an exception if device info cannot be retrieved.
  Future<DeviceInfo> getDeviceInfo();
}
