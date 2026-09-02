import 'package:dio/dio.dart';
import '../../../../core/services/token_cache_service.dart';

/// Interceptor that adds Bearer token to authenticated requests.
///
/// Reads the access token synchronously from [TokenCacheService] in-memory
/// cache — eliminates the first-request 401 race that occurred when reading
/// directly from FlutterSecureStorage (BUG-015).
class AuthTokenInterceptor extends Interceptor {
  AuthTokenInterceptor(this._tokenCache);

  final TokenCacheService _tokenCache;

  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) {
    // Skip token for auth endpoints
    if (_isAuthEndpoint(options.path)) {
      return handler.next(options);
    }

    final token = _tokenCache.accessToken;
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }

    handler.next(options);
  }

  bool _isAuthEndpoint(String path) {
    return path.endsWith('/auth/login') ||
        path.endsWith('/auth/register') ||
        path.endsWith('/auth/refresh') ||
        path.contains('/auth/verify') ||
        path.endsWith('/auth/resend-verification') ||
        path.endsWith('/auth/password/reset-request') ||
        path.endsWith('/auth/password/reset');
  }
}
