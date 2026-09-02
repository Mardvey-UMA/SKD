import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/repositories/i_onboarding_repository.dart';
import 'onboarding_provider.dart';

part 'topics_provider.g.dart';

@riverpod
class TopicsNotifier extends _$TopicsNotifier {
  @override
  Future<CategoriesResponse> build() async {
    final repo = ref.watch(onboardingRepositoryProvider);
    return repo.getCategories();
  }
}
