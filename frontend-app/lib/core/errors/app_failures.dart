import 'package:equatable/equatable.dart';

/// Base class for all application-layer failures.
abstract class AppFailure extends Equatable {
  const AppFailure(this.message);
  final String message;

  @override
  List<Object?> get props => [message];
}

// --- Generic typed failures ---

class ServerFailure extends AppFailure {
  const ServerFailure([super.message = 'Server error. Please try again.']);
}

class NetworkFailure extends AppFailure {
  const NetworkFailure([
    super.message = 'Network error. Check your connection.',
  ]);
}

class AuthFailure extends AppFailure {
  const AuthFailure([super.message = 'Authentication failed.']);

  /// Named constructor for specific error codes from backend.
  const AuthFailure.code(String code, super.msg);
}

class ValidationFailure extends AppFailure {
  const ValidationFailure([super.message = 'Validation error.']);
}

class NotFoundFailure extends AppFailure {
  const NotFoundFailure([super.message = 'Resource not found.']);
}

class ConflictFailure extends AppFailure {
  const ConflictFailure([super.message = 'Conflict with existing data.']);
}

// --- Onboarding ---

class TopicLoadFailure extends AppFailure {
  const TopicLoadFailure([super.message = 'Failed to load topics.']);
}

class OnboardingPersistFailure extends AppFailure {
  const OnboardingPersistFailure([
    super.message = 'Failed to save onboarding state.',
  ]);
}

// --- Feed ---

class FeedLoadFailure extends AppFailure {
  const FeedLoadFailure([super.message = 'Failed to load feed.']);
}

class ArticleActionFailure extends AppFailure {
  const ArticleActionFailure([
    super.message = 'Action failed. Please try again.',
  ]);
}

// --- Profile ---

class ProfileLoadFailure extends AppFailure {
  const ProfileLoadFailure([super.message = 'Failed to load profile.']);
}

// --- Collections ---

class CollectionLoadFailure extends AppFailure {
  const CollectionLoadFailure([super.message = 'Failed to load collections.']);
}

// --- Settings ---

class SettingsLoadFailure extends AppFailure {
  const SettingsLoadFailure([super.message = 'Failed to load settings.']);
}

class SettingsSaveFailure extends AppFailure {
  const SettingsSaveFailure([super.message = 'Failed to save settings.']);
}

// --- Email verification (code-based) ---

class InvalidCodeFailure extends AppFailure {
  const InvalidCodeFailure([super.message = 'Invalid verification code.']);
}

class TooManyAttemptsFailure extends AppFailure {
  const TooManyAttemptsFailure({
    this.retryAfterSeconds = 0,
    String message = 'Too many attempts. Please wait.',
  }) : super(message);

  final int retryAfterSeconds;

  @override
  List<Object?> get props => [...super.props, retryAfterSeconds];
}

class ResendCooldownFailure extends AppFailure {
  const ResendCooldownFailure({
    this.retryAfterSeconds = 60,
    String message = 'Please wait before resending the code.',
  }) : super(message);

  final int retryAfterSeconds;

  @override
  List<Object?> get props => [...super.props, retryAfterSeconds];
}

class UserNotFoundFailure extends AppFailure {
  const UserNotFoundFailure([super.message = 'User not found.']);
}

// --- Sources & Spaces (Phase 4) ---

class LimitReachedFailure extends AppFailure {
  const LimitReachedFailure({
    required this.limit,
    required this.kind,
    String message = 'Лимит исчерпан.',
  }) : super(message);

  final int limit;

  /// "source" | "space"
  final String kind;

  @override
  List<Object?> get props => [...super.props, limit, kind];
}

class DuplicateNameFailure extends AppFailure {
  const DuplicateNameFailure([super.message = 'Имя уже используется.']);
}

class SourceNotSupportedFailure extends AppFailure {
  const SourceNotSupportedFailure([super.message = 'Ссылка не распознана.']);
}

class PremiumRequiredFailure extends AppFailure {
  const PremiumRequiredFailure([super.message = 'Доступно только подписчикам.']);
}
