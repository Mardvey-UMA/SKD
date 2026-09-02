/// Service interface for secure token storage.
/// Implementation resides in Data layer using flutter_secure_storage.
abstract interface class ITokenStorageService {
  /// Stores access and refresh tokens securely.
  Future<void> storeTokens(String accessToken, String refreshToken);

  /// Retrieves the stored access token.
  /// Returns null if no token is stored.
  Future<String?> getAccessToken();

  /// Retrieves the stored refresh token.
  /// Returns null if no token is stored.
  Future<String?> getRefreshToken();

  /// Clears all stored tokens.
  Future<void> clearTokens();
}
