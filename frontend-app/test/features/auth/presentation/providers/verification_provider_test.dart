import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/errors/app_failures.dart';
import 'package:frontend_app/features/auth/data/models/token_pair.dart';
import 'package:frontend_app/features/auth/domain/repositories/i_auth_repository.dart';
import 'package:frontend_app/features/auth/presentation/providers/auth_repository_provider.dart';
import 'package:frontend_app/features/auth/presentation/providers/verification_provider.dart';

/// Configurable fake auth repository for provider tests.
class _FakeAuthRepository implements IAuthRepository {
  bool verifyEmailByCodeThrows = false;
  Object? verifyError;
  bool resendThrows = false;
  Object? resendError;

  @override
  Future<void> verifyEmailByCode(String email, String code) async {
    if (verifyEmailByCodeThrows) throw verifyError ?? Exception('verify error');
  }

  @override
  Future<void> resendVerificationCode(String email) async {
    if (resendThrows) throw resendError ?? Exception('resend error');
  }

  // --- unused ---
  @override
  Future<({String email, String message})> register(
          String email, String password) =>
      throw UnimplementedError();
  @override
  Future<TokenPair> login(String email, String password) =>
      throw UnimplementedError();
  @override
  Future<TokenPair> refresh(String refreshToken) => throw UnimplementedError();
  @override
  Future<void> logout() => throw UnimplementedError();
  @override
  Future<void> verifyEmail(String token) => throw UnimplementedError();
  @override
  Future<void> requestPasswordReset(String email) => throw UnimplementedError();
  @override
  Future<void> resetPassword(String token, String newPassword) =>
      throw UnimplementedError();
  @override
  Future<void> changePassword(String currentPassword, String newPassword) =>
      throw UnimplementedError();
  @override
  Future<String?> getAccessToken() async => null;
}

ProviderContainer _makeContainer(_FakeAuthRepository fakeRepo) {
  return ProviderContainer(
    overrides: [
      authRepositoryProvider.overrideWithValue(fakeRepo),
    ],
  );
}

void main() {
  group('VerificationNotifier.verify', () {
    test('idle → verifying → success on successful verify', () async {
      final repo = _FakeAuthRepository();
      final container = _makeContainer(repo);
      addTearDown(container.dispose);

      final notifier = container.read(verificationNotifierProvider.notifier);

      final states = <VerificationState>[];
      container.listen<AsyncValue<VerificationState>>(
        verificationNotifierProvider,
        (_, next) {
          if (next.hasValue) states.add(next.value!);
        },
        fireImmediately: true,
      );

      await notifier.verify('user@example.com', '123456');

      expect(states, contains(isA<VerificationVerifying>()));
      expect(states.last, isA<VerificationSuccess>());
    });

    test('transitions to error on InvalidCodeFailure', () async {
      final repo = _FakeAuthRepository()
        ..verifyEmailByCodeThrows = true
        ..verifyError = const InvalidCodeFailure();
      final container = _makeContainer(repo);
      addTearDown(container.dispose);

      await container
          .read(verificationNotifierProvider.notifier)
          .verify('user@example.com', 'wrong');

      final state = container.read(verificationNotifierProvider).value;
      expect(state, isA<VerificationError>());
      expect((state as VerificationError).message, contains('неверно'));
    });

    test('transitions to error on TooManyAttemptsFailure', () async {
      final repo = _FakeAuthRepository()
        ..verifyEmailByCodeThrows = true
        ..verifyError = const TooManyAttemptsFailure();
      final container = _makeContainer(repo);
      addTearDown(container.dispose);

      await container
          .read(verificationNotifierProvider.notifier)
          .verify('user@example.com', '000001');

      final state = container.read(verificationNotifierProvider).value;
      expect(state, isA<VerificationError>());
    });

    test('transitions to error on UserNotFoundFailure', () async {
      final repo = _FakeAuthRepository()
        ..verifyEmailByCodeThrows = true
        ..verifyError = const UserNotFoundFailure();
      final container = _makeContainer(repo);
      addTearDown(container.dispose);

      await container
          .read(verificationNotifierProvider.notifier)
          .verify('notfound@example.com', '000000');

      final state = container.read(verificationNotifierProvider).value;
      expect(state, isA<VerificationError>());
    });
  });

  group('VerificationNotifier.resend', () {
    test('triggers resend cooldown on success', () async {
      final repo = _FakeAuthRepository();
      final container = _makeContainer(repo);
      addTearDown(container.dispose);

      await container
          .read(verificationNotifierProvider.notifier)
          .resend('user@example.com');

      final state = container.read(verificationNotifierProvider).value;
      expect(state, isA<VerificationResendCooldown>());
      expect((state as VerificationResendCooldown).secondsRemaining, 60);
    });

    test('triggers cooldown on ResendCooldownFailure with server seconds',
        () async {
      final repo = _FakeAuthRepository()
        ..resendThrows = true
        ..resendError =
            const ResendCooldownFailure(retryAfterSeconds: 45);
      final container = _makeContainer(repo);
      addTearDown(container.dispose);

      await container
          .read(verificationNotifierProvider.notifier)
          .resend('user@example.com');

      final state = container.read(verificationNotifierProvider).value;
      expect(state, isA<VerificationResendCooldown>());
      expect((state as VerificationResendCooldown).secondsRemaining, 45);
    });
  });
}
