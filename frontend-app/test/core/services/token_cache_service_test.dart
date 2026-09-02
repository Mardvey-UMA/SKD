import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/services/token_cache_service.dart';

class _FakeSecureStorage implements FlutterSecureStorage {
  final Map<String, String?> _store = {};
  bool throwOnWrite = false;
  bool throwOnDelete = false;

  @override
  Future<String?> read({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async =>
      _store[key];

  @override
  Future<void> write({
    required String key,
    required String? value,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (throwOnWrite) throw Exception('storage write error');
    _store[key] = value;
  }

  @override
  Future<void> delete({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (throwOnDelete) throw Exception('storage delete error');
    _store.remove(key);
  }

  // --- unused interface methods ---
  @override
  Future<bool> containsKey({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async =>
      _store.containsKey(key);

  @override
  Future<Map<String, String>> readAll({
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async =>
      Map<String, String>.from(
        _store.entries
            .where((e) => e.value != null)
            .fold({}, (m, e) => m..[e.key] = e.value!),
      );

  @override
  Future<void> deleteAll({
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async =>
      _store.clear();

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  late _FakeSecureStorage storage;
  late TokenCacheService service;

  setUp(() {
    storage = _FakeSecureStorage();
    service = TokenCacheService(storage);
  });

  group('TokenCacheService.initialize', () {
    test('populates access and refresh tokens from storage', () async {
      storage._store['access_token'] = 'stored_access';
      storage._store['refresh_token'] = 'stored_refresh';

      await service.initialize();

      expect(service.accessToken, 'stored_access');
      expect(service.refreshToken, 'stored_refresh');
    });

    test('cache is null before initialize is called', () {
      expect(service.accessToken, isNull);
      expect(service.refreshToken, isNull);
    });

    test('handles null tokens in storage', () async {
      await service.initialize();

      expect(service.accessToken, isNull);
      expect(service.refreshToken, isNull);
    });
  });

  group('TokenCacheService.setTokens', () {
    test('updates both memory cache and underlying storage', () async {
      await service.setTokens(access: 'new_access', refresh: 'new_refresh');

      expect(service.accessToken, 'new_access');
      expect(service.refreshToken, 'new_refresh');
      expect(storage._store['access_token'], 'new_access');
      expect(storage._store['refresh_token'], 'new_refresh');
    });

    test('memory is updated even when storage throws', () async {
      storage.throwOnWrite = true;

      await service.setTokens(access: 'mem_access', refresh: 'mem_refresh');

      expect(service.accessToken, 'mem_access');
      expect(service.refreshToken, 'mem_refresh');
    });
  });

  group('TokenCacheService.clear', () {
    test('clears both memory and storage', () async {
      await service.setTokens(access: 'a', refresh: 'r');
      await service.clear();

      expect(service.accessToken, isNull);
      expect(service.refreshToken, isNull);
      expect(storage._store.containsKey('access_token'), isFalse);
      expect(storage._store.containsKey('refresh_token'), isFalse);
    });

    test('tolerates storage errors on clear', () async {
      await service.setTokens(access: 'a', refresh: 'r');
      storage.throwOnDelete = true;

      await service.clear();

      expect(service.accessToken, isNull);
      expect(service.refreshToken, isNull);
    });
  });
}
