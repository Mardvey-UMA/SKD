/// Form validation utilities for authentication forms.
class FormValidators {
  FormValidators._();

  static const int minPasswordLength = 8;

  /// Validates email format.
  /// Returns null if valid, error message if invalid.
  static String? validateEmail(String? value) {
    if (value == null || value.isEmpty) {
      return 'Введите эл. почту';
    }

    final emailRegex = RegExp(
      r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$',
    );

    if (!emailRegex.hasMatch(value)) {
      return 'Введите корректный адрес эл. почты';
    }

    return null;
  }

  /// Validates password strength.
  /// Returns null if valid, error message if invalid.
  static String? validatePassword(String? value) {
    if (value == null || value.isEmpty) {
      return 'Введите пароль';
    }

    if (value.length < minPasswordLength) {
      return 'Пароль должен содержать не менее $minPasswordLength символов';
    }

    return null;
  }

  /// Validates that passwords match.
  /// Returns null if valid, error message if invalid.
  static String? validatePasswordMatch(
    String? password,
    String? confirmPassword,
  ) {
    if (confirmPassword == null || confirmPassword.isEmpty) {
      return 'Подтвердите пароль';
    }

    if (password != confirmPassword) {
      return 'Пароли не совпадают';
    }

    return null;
  }

  /// Validates name field.
  /// Returns null if valid, error message if invalid.
  static String? validateName(String? value) {
    if (value == null || value.isEmpty) {
      return 'Введите имя';
    }

    if (value.trim().length < 2) {
      return 'Имя должно содержать не менее 2 символов';
    }

    return null;
  }
}
