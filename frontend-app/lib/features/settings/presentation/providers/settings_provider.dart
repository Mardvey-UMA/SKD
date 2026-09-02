import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/models/app_settings.dart';
import '../../domain/repositories/i_settings_repository.dart';
import '../../data/repositories/mock_settings_repository.dart';

part 'settings_provider.g.dart';

/// Provides the settings repository implementation.
/// TODO: Swap to ApiSettingsRepository when backend is ready.
final settingsRepositoryProvider = Provider<ISettingsRepository>((ref) {
  return MockSettingsRepository();
});

@riverpod
class SettingsNotifier extends _$SettingsNotifier {
  @override
  Future<AppSettings> build() async {
    final repo = ref.watch(settingsRepositoryProvider);
    return repo.getSettings();
  }

  Future<void> updatePushNotifications(bool enabled) async {
    final current = await future;
    state = AsyncData(current.copyWith(pushNotificationsEnabled: enabled));
    await ref
        .read(settingsRepositoryProvider)
        .updateSettings(state.requireValue);
  }

  Future<void> updateDigestEmails(bool enabled) async {
    final current = await future;
    state = AsyncData(current.copyWith(digestEmailsEnabled: enabled));
    await ref
        .read(settingsRepositoryProvider)
        .updateSettings(state.requireValue);
  }
}
