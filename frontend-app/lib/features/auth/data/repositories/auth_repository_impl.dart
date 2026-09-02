import '../../domain/repositories/i_auth_repository.dart';
import '../../domain/services/i_token_storage_service.dart';
import '../models/token_pair.dart';

/// Mock implementation of [IAuthRepository] for development/testing reference.
/// NOT injected in production — use [ApiAuthRepository] instead.
class MockAuthRepository implements IAuthRepository {
  MockAuthRepository(this._tokenStorage);

  final ITokenStorageService _tokenStorage;

  @override
  Future<({String message, String email})> register(
    String email,
    String password,
  ) async {
    await Future.delayed(const Duration(seconds: 1));
    if (email.isEmpty || password.isEmpty) {
      throw Exception('All fields are required');
    }
    return (
      message:
          'Registration successful. Please check your email to verify your account.',
      email: email,
    );
  }

  @override
  Future<TokenPair> login(String email, String password) async {
    await Future.delayed(const Duration(seconds: 1));
    if (email.isEmpty || password.isEmpty) {
      throw Exception('Invalid credentials');
    }
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final pair = TokenPair(
      accessToken: 'mock_access_token_$timestamp',
      refreshToken: 'mock_refresh_token_$timestamp',
    );
    await _tokenStorage.storeTokens(pair.accessToken, pair.refreshToken);
    return pair;
  }

  @override
  Future<TokenPair> refresh(String refreshToken) async {
    await Future.delayed(const Duration(milliseconds: 500));
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final pair = TokenPair(
      accessToken: 'mock_access_token_$timestamp',
      refreshToken: 'mock_refresh_token_$timestamp',
    );
    await _tokenStorage.storeTokens(pair.accessToken, pair.refreshToken);
    return pair;
  }

  @override
  Future<void> logout() async {
    await Future.delayed(const Duration(milliseconds: 500));
    await _tokenStorage.clearTokens();
  }

  @override
  Future<void> verifyEmail(String token) async {
    await Future.delayed(const Duration(milliseconds: 500));
  }

  @override
  Future<void> requestPasswordReset(String email) async {
    await Future.delayed(const Duration(milliseconds: 500));
  }

  @override
  Future<void> resetPassword(String token, String newPassword) async {
    await Future.delayed(const Duration(milliseconds: 500));
  }

  @override
  Future<void> changePassword(
    String currentPassword,
    String newPassword,
  ) async {
    await Future.delayed(const Duration(milliseconds: 500));
  }

  @override
  Future<void> verifyEmailByCode(String email, String code) async {
    await Future.delayed(const Duration(milliseconds: 500));
    // In dev mode, code 000000 is always valid
    if (code != '000000') {
      throw Exception('INVALID_CODE');
    }
  }

  @override
  Future<void> resendVerificationCode(String email) async {
    await Future.delayed(const Duration(milliseconds: 500));
  }

  @override
  Future<String?> getAccessToken() => _tokenStorage.getAccessToken();
}
