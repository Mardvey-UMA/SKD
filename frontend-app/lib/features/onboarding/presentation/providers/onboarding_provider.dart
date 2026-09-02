import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/repositories/i_onboarding_repository.dart';
import '../../data/repositories/api_onboarding_repository.dart';
import '../../../auth/presentation/providers/dio_provider.dart';

part 'onboarding_provider.g.dart';

/// Provides the onboarding repository implementation.
final onboardingRepositoryProvider = Provider<IOnboardingRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiOnboardingRepository(dio);
});

@riverpod
Future<bool> onboardingStatus(Ref ref) async {
  final repo = ref.watch(onboardingRepositoryProvider);
  return repo.isOnboardingCompleted();
}
