import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../domain/services/i_token_storage_service.dart';

/// Implementation of token storage using FlutterSecureStorage.
class TokenStorageServiceImpl implements ITokenStorageService {
  TokenStorageServiceImpl(this._storage);

  final FlutterSecureStorage _storage;
  final Map<String, String> _memoryStorage = {};

  static const String _accessTokenKey = 'access_token';
  static const String _refreshTokenKey = 'refresh_token';

  @override
  Future<void> storeTokens(String accessToken, String refreshToken) async {
    try {
      await Future.wait([
        _storage.write(key: _accessTokenKey, value: accessToken),
        _storage.write(key: _refreshTokenKey, value: refreshToken),
      ]);
    } catch (_) {
      // Fallback to in-memory storage if secure storage fails (e.g., Linux without keyring)
      _memoryStorage[_accessTokenKey] = accessToken;
      _memoryStorage[_refreshTokenKey] = refreshToken;
    }
  }

  @override
  Future<String?> getAccessToken() async {
    try {
      return await _storage.read(key: _accessTokenKey) ??
          _memoryStorage[_accessTokenKey];
    } catch (_) {
      return _memoryStorage[_accessTokenKey];
    }
  }

  @override
  Future<String?> getRefreshToken() async {
    try {
      return await _storage.read(key: _refreshTokenKey) ??
          _memoryStorage[_refreshTokenKey];
    } catch (_) {
      return _memoryStorage[_refreshTokenKey];
    }
  }

  @override
  Future<void> clearTokens() async {
    try {
      await Future.wait([
        _storage.delete(key: _accessTokenKey),
        _storage.delete(key: _refreshTokenKey),
      ]);
    } catch (_) {
      // Ignore secure storage errors on clearing
    }
    _memoryStorage.clear();
  }
}
