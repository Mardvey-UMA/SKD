import 'package:dio/dio.dart';
import 'api_error.dart';
import 'app_failures.dart';

/// Maps DioException + HTTP status codes to typed [AppFailure] objects.
class DioErrorHandler {
  DioErrorHandler._();

  /// Converts a [DioException] to an appropriate [AppFailure].
  static AppFailure handle(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.connectionError:
        return const NetworkFailure();
      case DioExceptionType.badResponse:
        return _handleResponse(e.response);
      case DioExceptionType.cancel:
        return const NetworkFailure('Request cancelled.');
      default:
        return const NetworkFailure();
    }
  }

  static AppFailure _handleResponse(Response? response) {
    if (response == null) return const ServerFailure();

    ApiError? apiError;
    try {
      final data = response.data;
      if (data is Map<String, dynamic>) {
        apiError = ApiError.fromJson(data);
      }
    } catch (_) {}

    final message = apiError?.message ?? 'An error occurred.';
    final code = apiError?.error ?? '';

    switch (response.statusCode ?? 500) {
      case 400:
        if (code == 'token_expired') return AuthFailure(message);
        if (code == 'source_not_supported' || code == 'source_invalid') {
          return SourceNotSupportedFailure(message);
        }
        if (code == 'space_requires_sources' ||
            code == 'narrow_mode_requires_include_source_ids') {
          return ValidationFailure(message);
        }
        return ValidationFailure(message);
      case 401:
        return AuthFailure(message);
      case 403:
        if (code == 'premium_required') return PremiumRequiredFailure(message);
        return AuthFailure(message);
      case 404:
        return NotFoundFailure(message);
      case 409:
        if (code == 'duplicate_space_name' ||
            code == 'space_name_duplicate' ||
            code == 'source_name_duplicate') {
          return DuplicateNameFailure(message);
        }
        return ConflictFailure(message);
      case 422:
        return ValidationFailure(message);
      case 429:
        if (code == 'source_limit_reached') {
          return LimitReachedFailure(
            limit: 20,
            kind: 'source',
            message: message,
          );
        }
        if (code == 'space_limit_reached') {
          return LimitReachedFailure(
            limit: 10,
            kind: 'space',
            message: message,
          );
        }
        return ServerFailure(message);
      case >= 500:
        return ServerFailure(message);
      default:
        return ServerFailure(message);
    }
  }
}
