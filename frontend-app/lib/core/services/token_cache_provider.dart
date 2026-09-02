import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/auth/presentation/providers/token_storage_provider.dart';
import 'token_cache_service.dart';

/// Provider for [TokenCacheService].
/// Created eagerly and kept alive — tokens must survive route changes.
final tokenCacheServiceProvider = Provider<TokenCacheService>((ref) {
  final storage = ref.watch(flutterSecureStorageProvider);
  return TokenCacheService(storage);
});

/// Bootstrap provider — initialises the token cache from secure storage.
/// Widgets that depend on authenticated requests should await this provider.
final tokenCacheInitProvider = FutureProvider<void>((ref) async {
  final cache = ref.watch(tokenCacheServiceProvider);
  await cache.initialize();
});
