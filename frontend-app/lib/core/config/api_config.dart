import 'package:flutter/foundation.dart';

/// API configuration for different environments.
class ApiConfig {
  ApiConfig._();

  static const String _envBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  /// Base URL of the API gateway.
  /// Web dev: http://localhost:8080 (api-gateway).
  /// Web prod: empty string (nginx proxies /api/ to api-gateway).
  /// Android emulator: 10.0.2.2:8080 → host machine's localhost:8080
  static String get baseUrl {
    if (_envBaseUrl.isNotEmpty) return _envBaseUrl;
    if (kIsWeb) return 'http://localhost:18080';
    return 'http://10.0.2.2:8080';
  }
}
