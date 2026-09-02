import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../../core/validators/form_validators.dart';
import '../models/login_form_state.dart';

part 'login_form_provider.g.dart';

/// Provider for login form state management.
@riverpod
class LoginFormNotifier extends _$LoginFormNotifier {
  @override
  LoginFormState build() => const LoginFormState();

  /// Updates email field.
  void setEmail(String email) {
    state = state.copyWith(
      email: email,
      emailError: null, // Clear error on change
    );
  }

  /// Updates password field.
  void setPassword(String password) {
    state = state.copyWith(
      password: password,
      passwordError: null, // Clear error on change
    );
  }

  /// Toggles password visibility.
  void togglePasswordVisibility() {
    state = state.copyWith(obscurePassword: !state.obscurePassword);
  }

  /// Validates all fields.
  /// Returns true if valid, false otherwise.
  bool validate() {
    final emailError = FormValidators.validateEmail(state.email);
    final passwordError = FormValidators.validatePassword(state.password);

    state = state.copyWith(
      emailError: emailError,
      passwordError: passwordError,
    );

    return emailError == null && passwordError == null;
  }

  /// Sets submitting state.
  void setSubmitting(bool isSubmitting) {
    state = state.copyWith(isSubmitting: isSubmitting);
  }

  /// Resets form to initial state.
  void reset() {
    state = const LoginFormState();
  }
}
