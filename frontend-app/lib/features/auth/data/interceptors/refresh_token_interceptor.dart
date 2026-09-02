import 'package:dio/dio.dart';
import '../../../../core/services/token_cache_service.dart';
import '../models/token_pair.dart';

/// Interceptor that handles 401 responses and refreshes tokens.
/// Uses QueuedInterceptor to ensure sequential token refresh.
class RefreshTokenInterceptor extends QueuedInterceptor {
  RefreshTokenInterceptor(this._dio, this._tokenCache);

  final Dio _dio;
  final TokenCacheService _tokenCache;
  bool _isRefreshing = false;

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    // Only handle 401 Unauthorized
    if (err.response?.statusCode != 401) {
      return handler.next(err);
    }

    // Skip refresh for auth endpoints
    if (_isAuthEndpoint(err.requestOptions.path)) {
      return handler.next(err);
    }

    // Try to refresh token
    if (!_isRefreshing) {
      _isRefreshing = true;
      try {
        final refreshToken = _tokenCache.refreshToken;
        if (refreshToken == null) {
          await _tokenCache.clear();
          _isRefreshing = false;
          return handler.next(err);
        }

        // Call refresh endpoint without auth header
        final response = await _dio.post<Map<String, dynamic>>(
          '/api/auth/refresh',
          data: {'refresh_token': refreshToken},
          options: Options(headers: {'Authorization': ''}),
        );

        final tokenPair = TokenPair.fromJson(response.data!);
        await _tokenCache.setTokens(
          access: tokenPair.accessToken,
          refresh: tokenPair.refreshToken,
        );
      } catch (e) {
        // Refresh failed, clear tokens
        await _tokenCache.clear();
        _isRefreshing = false;
        return handler.next(err);
      }
      _isRefreshing = false;
    }

    // Retry original request with new token
    final options = err.requestOptions;
    final newToken = _tokenCache.accessToken;
    if (newToken != null) {
      options.headers['Authorization'] = 'Bearer $newToken';
      try {
        final response = await _dio.fetch(options);
        return handler.resolve(response);
      } catch (e) {
        return handler.next(err);
      }
    }

    handler.next(err);
  }

  bool _isAuthEndpoint(String path) {
    return path.endsWith('/auth/login') ||
        path.endsWith('/auth/register') ||
        path.endsWith('/auth/refresh');
  }
}
