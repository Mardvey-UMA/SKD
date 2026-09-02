import '../models/topic.dart';

/// Response from getCategories including min/max selection constraints.
class CategoriesResponse {
  const CategoriesResponse({
    required this.categories,
    required this.minSelect,
    required this.maxSelect,
  });

  final List<Topic> categories;
  final int minSelect;
  final int maxSelect;
}

abstract interface class IOnboardingRepository {
  Future<CategoriesResponse> getCategories();
  Future<void> completeOnboarding(
    List<String> categories,
    List<String> sourceContentIds,
  );
  Future<bool> isOnboardingCompleted();
  Future<void> clearOnboarding();
}
