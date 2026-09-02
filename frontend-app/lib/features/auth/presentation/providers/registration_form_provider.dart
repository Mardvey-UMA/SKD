import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../../core/validators/form_validators.dart';
import '../models/registration_form_state.dart';

part 'registration_form_provider.g.dart';

/// Provider for registration form state management.
@riverpod
class RegistrationFormNotifier extends _$RegistrationFormNotifier {
  @override
  RegistrationFormState build() => const RegistrationFormState();

  /// Updates email field.
  void setEmail(String email) {
    state = state.copyWith(email: email, emailError: null);
  }

  /// Updates password field.
  void setPassword(String password) {
    state = state.copyWith(password: password, passwordError: null);
  }

  /// Updates confirm password field.
  void setConfirmPassword(String confirmPassword) {
    state = state.copyWith(
      confirmPassword: confirmPassword,
      confirmPasswordError: null,
    );
  }

  /// Toggles password visibility.
  void togglePasswordVisibility() {
    state = state.copyWith(obscurePassword: !state.obscurePassword);
  }

  /// Validates all fields. Returns true if valid.
  bool validate() {
    final emailError = FormValidators.validateEmail(state.email);
    final passwordError = FormValidators.validatePassword(state.password);
    final confirmPasswordError = FormValidators.validatePasswordMatch(
      state.password,
      state.confirmPassword,
    );

    state = state.copyWith(
      emailError: emailError,
      passwordError: passwordError,
      confirmPasswordError: confirmPasswordError,
    );

    return emailError == null &&
        passwordError == null &&
        confirmPasswordError == null;
  }

  /// Sets submitting state.
  void setSubmitting(bool isSubmitting) {
    state = state.copyWith(isSubmitting: isSubmitting);
  }

  /// Resets form to initial state.
  void reset() {
    state = const RegistrationFormState();
  }
}
