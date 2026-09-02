import 'package:equatable/equatable.dart';

/// State model for login form.
class LoginFormState extends Equatable {
  const LoginFormState({
    this.email = '',
    this.password = '',
    this.emailError,
    this.passwordError,
    this.isSubmitting = false,
    this.obscurePassword = true,
  });

  final String email;
  final String password;
  final String? emailError;
  final String? passwordError;
  final bool isSubmitting;
  final bool obscurePassword;

  /// Whether the form has any validation errors.
  bool get hasErrors => emailError != null || passwordError != null;

  /// Whether the form is valid for submission.
  bool get isValid => email.isNotEmpty && password.isNotEmpty && !hasErrors;

  LoginFormState copyWith({
    String? email,
    String? password,
    String? emailError,
    String? passwordError,
    bool? isSubmitting,
    bool? obscurePassword,
  }) {
    return LoginFormState(
      email: email ?? this.email,
      password: password ?? this.password,
      emailError: emailError,
      passwordError: passwordError,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      obscurePassword: obscurePassword ?? this.obscurePassword,
    );
  }

  @override
  List<Object?> get props => [
    email,
    password,
    emailError,
    passwordError,
    isSubmitting,
    obscurePassword,
  ];
}
