import '../../domain/models/app_settings.dart';
import '../../domain/repositories/i_settings_repository.dart';

/// Mock implementation of ISettingsRepository.
/// Stores settings in-memory; initial state matches SettingsScreen.png.
/// TODO: Swap to ApiSettingsRepository when backend is ready.
class MockSettingsRepository implements ISettingsRepository {
  AppSettings _settings = const AppSettings(
    email: 'arivera@example.com',
    pushNotificationsEnabled: true,
    digestEmailsEnabled: false,
    language: 'English',
    hiddenCategoriesCount: 2,
  );

  @override
  Future<AppSettings> getSettings() async {
    await Future.delayed(const Duration(milliseconds: 200));
    return _settings;
  }

  @override
  Future<void> updateSettings(AppSettings settings) async {
    await Future.delayed(const Duration(milliseconds: 200));
    _settings = settings;
  }
}
