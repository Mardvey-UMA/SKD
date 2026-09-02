import '../../data/models/token_pair.dart';

/// Repository interface for authentication operations.
abstract interface class IAuthRepository {
  /// Registers a new user with email and password.
  /// Returns {message, email} on success.
  /// Backend requires email verification before login.
  Future<({String message, String email})> register(
    String email,
    String password,
  );

  /// Authenticates user with email and password.
  /// Returns a [TokenPair] and stores tokens securely.
  Future<TokenPair> login(String email, String password);

  /// Refreshes the access token using the given refresh token.
  /// Returns a new [TokenPair].
  Future<TokenPair> refresh(String refreshToken);

  /// Logs out the current user (revokes tokens server-side + clears local).
  Future<void> logout();

  /// Verifies email using the token from the verification email.
  Future<void> verifyEmail(String token);

  /// Requests a password reset email.
  Future<void> requestPasswordReset(String email);

  /// Resets password using the token from the reset email.
  Future<void> resetPassword(String token, String newPassword);

  /// Changes password for an authenticated user.
  Future<void> changePassword(String currentPassword, String newPassword);

  /// Verifies email using a 6-digit code entered by the user.
  Future<void> verifyEmailByCode(String email, String code);

  /// Requests a new verification code to be sent (or accepted in dev mode).
  Future<void> resendVerificationCode(String email);

  /// Returns the stored access token, or null if not authenticated.
  Future<String?> getAccessToken();
}
