import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/services/token_cache_provider.dart';
import '../../data/repositories/api_auth_repository.dart';
import '../../domain/repositories/i_auth_repository.dart';
import 'dio_provider.dart';

/// Provider for auth repository.
/// Uses real API implementation against api-gateway :8080.
final authRepositoryProvider = Provider<IAuthRepository>((ref) {
  final dio = ref.watch(dioProvider);
  final tokenCache = ref.watch(tokenCacheServiceProvider);
  return ApiAuthRepository(dio, tokenCache);
});
