import 'package:dio/dio.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../domain/models/user_profile.dart';
import '../../domain/repositories/i_profile_repository.dart';
import '../dto/user_profile_dto.dart';

/// Real API implementation of [IProfileRepository].
/// Communicates with api-gateway :8080 via Dio.
class ApiProfileRepository implements IProfileRepository {
  ApiProfileRepository(this._dio);

  final Dio _dio;

  @override
  Future<UserProfile> getUserProfile() async {
    // Retry up to 3 times with 3-second delays for 404 (profile not yet created)
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        final response = await _dio.get<Map<String, dynamic>>('/api/users/me');
        return UserProfileDto.fromJson(response.data!).toDomain();
      } on DioException catch (e) {
        final failure = DioErrorHandler.handle(e);
        if (failure is NotFoundFailure && attempt < 2) {
          await Future.delayed(const Duration(seconds: 3));
          continue;
        }
        throw failure;
      }
    }
    throw const NotFoundFailure('Profile not found after retries.');
  }

  @override
  Future<UserProfile> updateProfile({
    String? displayName,
    String? avatarUrl,
  }) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/users/me',
        data: {
          if (displayName != null) 'display_name': displayName,
          if (avatarUrl != null) 'avatar_url': avatarUrl,
        },
      );
      return UserProfileDto.fromJson(response.data!).toDomain();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
