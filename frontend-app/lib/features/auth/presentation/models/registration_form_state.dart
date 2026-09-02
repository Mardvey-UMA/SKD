import 'package:equatable/equatable.dart';

/// State model for registration form (email + password only).
class RegistrationFormState extends Equatable {
  const RegistrationFormState({
    this.email = '',
    this.password = '',
    this.confirmPassword = '',
    this.emailError,
    this.passwordError,
    this.confirmPasswordError,
    this.isSubmitting = false,
    this.obscurePassword = true,
  });

  final String email;
  final String password;
  final String confirmPassword;
  final String? emailError;
  final String? passwordError;
  final String? confirmPasswordError;
  final bool isSubmitting;
  final bool obscurePassword;

  bool get hasErrors =>
      emailError != null ||
      passwordError != null ||
      confirmPasswordError != null;

  bool get isValid =>
      email.isNotEmpty &&
      password.isNotEmpty &&
      confirmPassword.isNotEmpty &&
      !hasErrors;

  RegistrationFormState copyWith({
    String? email,
    String? password,
    String? confirmPassword,
    String? emailError,
    String? passwordError,
    String? confirmPasswordError,
    bool? isSubmitting,
    bool? obscurePassword,
  }) {
    return RegistrationFormState(
      email: email ?? this.email,
      password: password ?? this.password,
      confirmPassword: confirmPassword ?? this.confirmPassword,
      emailError: emailError,
      passwordError: passwordError,
      confirmPasswordError: confirmPasswordError,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      obscurePassword: obscurePassword ?? this.obscurePassword,
    );
  }

  @override
  List<Object?> get props => [
    email,
    password,
    confirmPassword,
    emailError,
    passwordError,
    confirmPasswordError,
    isSubmitting,
    obscurePassword,
  ];
}
