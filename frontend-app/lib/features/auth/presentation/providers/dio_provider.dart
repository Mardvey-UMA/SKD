import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/config/api_config.dart';
import '../../../../core/services/token_cache_provider.dart';
import '../../data/interceptors/auth_token_interceptor.dart';
import '../../data/interceptors/refresh_token_interceptor.dart';

/// Provider for configured Dio client with auth interceptors.
final dioProvider = Provider<Dio>((ref) {
  final tokenCache = ref.watch(tokenCacheServiceProvider);

  final dio = Dio(
    BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ),
  );

  // Add interceptors
  dio.interceptors.addAll([
    AuthTokenInterceptor(tokenCache),
    RefreshTokenInterceptor(dio, tokenCache),
  ]);

  return dio;
});
