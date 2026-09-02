import '../models/app_settings.dart';

abstract interface class ISettingsRepository {
  Future<AppSettings> getSettings();
  Future<void> updateSettings(AppSettings settings);
}
