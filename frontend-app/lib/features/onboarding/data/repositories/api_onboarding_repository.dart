import 'package:dio/dio.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../domain/repositories/i_onboarding_repository.dart';
import '../dto/topic_dto.dart';

/// Real API implementation of [IOnboardingRepository].
/// Communicates with api-gateway :8080 via Dio.
class ApiOnboardingRepository implements IOnboardingRepository {
  ApiOnboardingRepository(this._dio);

  final Dio _dio;

  @override
  Future<CategoriesResponse> getCategories() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/users/me/categories',
      );
      final data = response.data!;
      final categories = (data['categories'] as List)
          .map((e) => TopicDto.fromJson(e as Map<String, dynamic>).toDomain())
          .toList();
      return CategoriesResponse(
        categories: categories,
        minSelect: (data['min_select'] as num).toInt(),
        maxSelect: (data['max_select'] as num).toInt(),
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> completeOnboarding(
    List<String> categories,
    List<String> sourceContentIds,
  ) async {
    try {
      await _dio.post<Map<String, dynamic>>(
        '/api/users/me/onboarding',
        data: {
          'categories': categories,
          'source_content_ids': sourceContentIds,
        },
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<bool> isOnboardingCompleted() async {
    // Retry up to 3 times with 3-second delays for 404 (profile not yet created)
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        final response = await _dio.get<Map<String, dynamic>>('/api/users/me');
        return (response.data!['onboarding_completed'] as bool?) ?? false;
      } on DioException catch (e) {
        final failure = DioErrorHandler.handle(e);
        if (failure is NotFoundFailure && attempt < 2) {
          await Future.delayed(const Duration(seconds: 3));
          continue;
        }
        throw failure;
      }
    }
    return false;
  }

  @override
  Future<void> clearOnboarding() async {
    // No backend endpoint to clear onboarding — no-op for API implementation.
  }
}
