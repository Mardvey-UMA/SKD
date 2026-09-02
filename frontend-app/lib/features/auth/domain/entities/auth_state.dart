import 'package:equatable/equatable.dart';

/// Domain model representing authentication state.
class AuthState extends Equatable {
  const AuthState({
    this.isAuthenticated = false,
    this.pendingEmailVerification = false,
    this.pendingCodeVerification = false,
    this.pendingEmail,
    this.registrationComplete = false,
  });

  /// Initial unauthenticated state.
  const AuthState.initial() : this();

  /// Authenticated state — tokens stored securely.
  const AuthState.authenticated() : this(isAuthenticated: true);

  /// Unauthenticated state.
  const AuthState.unauthenticated() : this();

  /// State after registration — email auto-verified, navigate to login.
  const AuthState.registrationComplete() : this(registrationComplete: true);

  /// State after registration — awaiting 6-digit code verification.
  const AuthState.pendingCodeVerification(String email)
    : this(pendingCodeVerification: true, pendingEmail: email);

  /// State after registration — awaiting email verification (legacy link-based).
  const AuthState.emailVerificationPending(String email)
    : this(pendingEmailVerification: true, pendingEmail: email);

  final bool isAuthenticated;
  final bool pendingEmailVerification;

  /// True after registration — user must enter the 6-digit code.
  final bool pendingCodeVerification;
  final String? pendingEmail;
  final bool registrationComplete;

  @override
  List<Object?> get props => [
    isAuthenticated,
    pendingEmailVerification,
    pendingCodeVerification,
    pendingEmail,
    registrationComplete,
  ];

  @override
  String toString() =>
      'AuthState(isAuthenticated: $isAuthenticated, '
      'pendingCodeVerification: $pendingCodeVerification, '
      'pendingEmailVerification: $pendingEmailVerification, '
      'registrationComplete: $registrationComplete)';
}
