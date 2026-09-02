import 'package:dio/dio.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../../../core/services/token_cache_service.dart';
import '../../domain/repositories/i_auth_repository.dart';
import '../models/token_pair.dart';

/// Real API implementation of [IAuthRepository].
/// Communicates with api-gateway :8080 via Dio.
class ApiAuthRepository implements IAuthRepository {
  ApiAuthRepository(this._dio, this._tokenCache);

  final Dio _dio;
  final TokenCacheService _tokenCache;

  @override
  Future<({String message, String email})> register(
    String email,
    String password,
  ) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/auth/register',
        data: {'email': email, 'password': password},
      );
      final data = response.data!;
      return (
        message: data['message'] as String,
        email: data['email'] as String,
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<TokenPair> login(String email, String password) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/auth/login',
        data: {'email': email, 'password': password},
      );
      final pair = TokenPair.fromJson(response.data!);
      await _tokenCache.setTokens(
        access: pair.accessToken,
        refresh: pair.refreshToken,
      );
      return pair;
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<TokenPair> refresh(String refreshToken) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/auth/refresh',
        data: {'refresh_token': refreshToken},
        options: Options(headers: {'Authorization': ''}),
      );
      final pair = TokenPair.fromJson(response.data!);
      await _tokenCache.setTokens(
        access: pair.accessToken,
        refresh: pair.refreshToken,
      );
      return pair;
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> logout() async {
    try {
      await _dio.post<void>('/api/auth/logout');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    } finally {
      await _tokenCache.clear();
    }
  }

  @override
  Future<void> verifyEmail(String token) async {
    try {
      await _dio.get<void>(
        '/api/auth/verify',
        queryParameters: {'token': token},
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> requestPasswordReset(String email) async {
    try {
      await _dio.post<void>(
        '/api/auth/password/reset-request',
        data: {'email': email},
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> resetPassword(String token, String newPassword) async {
    try {
      await _dio.post<void>(
        '/api/auth/password/reset',
        data: {'token': token, 'new_password': newPassword},
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> changePassword(
    String currentPassword,
    String newPassword,
  ) async {
    try {
      await _dio.post<void>(
        '/api/auth/password/change',
        data: {
          'current_password': currentPassword,
          'new_password': newPassword,
        },
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> verifyEmailByCode(String email, String code) async {
    try {
      await _dio.post<void>(
        '/api/auth/verify',
        data: {'email': email, 'code': code},
      );
    } on DioException catch (e) {
      throw _mapVerificationError(e);
    }
  }

  @override
  Future<void> resendVerificationCode(String email) async {
    try {
      await _dio.post<void>(
        '/api/auth/resend-verification',
        data: {'email': email},
      );
    } on DioException catch (e) {
      throw _mapResendError(e);
    }
  }

  @override
  Future<String?> getAccessToken() async => _tokenCache.accessToken;

  AppFailure _mapVerificationError(DioException e) {
    if (e.response == null) return const NetworkFailure();
    final statusCode = e.response!.statusCode ?? 500;
    final data = e.response!.data;
    ApiError? apiError;
    try {
      if (data is Map<String, dynamic>) {
        apiError = ApiError.fromJson(data);
      }
    } catch (_) {}

    final errorCode = apiError?.error ?? '';
    final retryAfter = (data is Map<String, dynamic>)
        ? (data['retryAfterSeconds'] as int?) ?? 0
        : 0;

    switch (statusCode) {
      case 400:
        if (errorCode == 'INVALID_CODE') return const InvalidCodeFailure();
        return ValidationFailure(apiError?.message ?? 'Validation error.');
      case 404:
        return const UserNotFoundFailure();
      case 429:
        return TooManyAttemptsFailure(retryAfterSeconds: retryAfter);
      default:
        return DioErrorHandler.handle(e);
    }
  }

  AppFailure _mapResendError(DioException e) {
    if (e.response == null) return const NetworkFailure();
    final statusCode = e.response!.statusCode ?? 500;
    final data = e.response!.data;
    ApiError? apiError;
    try {
      if (data is Map<String, dynamic>) {
        apiError = ApiError.fromJson(data);
      }
    } catch (_) {}

    final errorCode = apiError?.error ?? '';
    final retryAfter = (data is Map<String, dynamic>)
        ? (data['retryAfterSeconds'] as int?) ?? 60
        : 60;

    switch (statusCode) {
      case 404:
        return const UserNotFoundFailure();
      case 429:
        if (errorCode == 'RESEND_COOLDOWN') {
          return ResendCooldownFailure(retryAfterSeconds: retryAfter);
        }
        return TooManyAttemptsFailure(retryAfterSeconds: retryAfter);
      default:
        return DioErrorHandler.handle(e);
    }
  }
}
