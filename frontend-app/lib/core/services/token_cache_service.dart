import 'dart:developer' as developer;

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'web_storage_stub.dart'
    if (dart.library.js_interop) 'web_storage_impl.dart';

/// In-memory cache for JWT tokens backed by FlutterSecureStorage.
///
/// On Web: also persists to localStorage so tokens survive page refresh.
/// FlutterSecureStorage uses IndexedDB on web which is unreliable across
/// debug hot restarts (encryption key changes). localStorage is the reliable
/// fallback.
class TokenCacheService {
  TokenCacheService(this._storage);

  final FlutterSecureStorage _storage;
  String? _cachedAccessToken;
  String? _cachedRefreshToken;

  static const _accessKey = 'access_token';
  static const _refreshKey = 'refresh_token';

  /// Pre-loads tokens from persistent storage at app startup.
  Future<void> initialize() async {
    // Try FlutterSecureStorage first with a timeout to avoid hanging on Web IndexedDB
    try {
      final results = await Future.wait([
        _storage.read(key: _accessKey),
        _storage.read(key: _refreshKey),
      ]).timeout(const Duration(seconds: 3), onTimeout: () => [null, null]);
      _cachedAccessToken = results[0];
      _cachedRefreshToken = results[1];
    } catch (e) {
      developer.log('SecureStorage read failed: $e', name: 'TokenCache');
      _cachedAccessToken = null;
      _cachedRefreshToken = null;
    }

    // On Web, fall back to localStorage if secure storage returned nothing
    if (kIsWeb && _cachedAccessToken == null) {
      try {
        _cachedAccessToken = webStorageRead(_accessKey);
        _cachedRefreshToken = webStorageRead(_refreshKey);
        if (_cachedAccessToken != null) {
          developer.log('Restored tokens from localStorage', name: 'TokenCache');
        }
      } catch (_) {}
    }
  }

  String? get accessToken => _cachedAccessToken;
  String? get refreshToken => _cachedRefreshToken;

  /// Stores both tokens in memory and persists to storage.
  Future<void> setTokens({
    required String access,
    required String refresh,
  }) async {
    _cachedAccessToken = access;
    _cachedRefreshToken = refresh;

    // Persist to FlutterSecureStorage (best effort)
    try {
      await Future.wait([
        _storage.write(key: _accessKey, value: access),
        _storage.write(key: _refreshKey, value: refresh),
      ]);
    } catch (_) {}

    // On Web, also persist to localStorage as reliable fallback
    if (kIsWeb) {
      try {
        webStorageWrite(_accessKey, access);
        webStorageWrite(_refreshKey, refresh);
      } catch (_) {}
    }
  }

  /// Clears both tokens from all storage layers.
  Future<void> clear() async {
    _cachedAccessToken = null;
    _cachedRefreshToken = null;
    try {
      await Future.wait([
        _storage.delete(key: _accessKey),
        _storage.delete(key: _refreshKey),
      ]);
    } catch (_) {}
    if (kIsWeb) {
      try {
        webStorageRemove(_accessKey);
        webStorageRemove(_refreshKey);
      } catch (_) {}
    }
  }
}
