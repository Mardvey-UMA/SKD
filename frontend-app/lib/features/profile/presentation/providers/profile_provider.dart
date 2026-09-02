import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/models/user_profile.dart';
import '../../domain/repositories/i_profile_repository.dart';
import '../../data/repositories/api_profile_repository.dart';
import '../../../auth/presentation/providers/dio_provider.dart';

part 'profile_provider.g.dart';

/// Provides the profile repository implementation.
final profileRepositoryProvider = Provider<IProfileRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiProfileRepository(dio);
});

@riverpod
class UserProfileNotifier extends _$UserProfileNotifier {
  @override
  Future<UserProfile> build() async {
    final repo = ref.watch(profileRepositoryProvider);
    return repo.getUserProfile();
  }

  Future<void> updateProfile({String? displayName, String? avatarUrl}) async {
    final repo = ref.read(profileRepositoryProvider);
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => repo.updateProfile(displayName: displayName, avatarUrl: avatarUrl),
    );
  }
}
