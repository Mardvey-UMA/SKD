import 'dart:async';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../domain/repositories/i_auth_repository.dart';
import '../../../../core/errors/app_failures.dart';
import 'auth_provider.dart';
import 'auth_repository_provider.dart';

part 'verification_provider.g.dart';

/// Represents the current state of the email verification flow.
sealed class VerificationState {
  const VerificationState();
}

final class VerificationIdle extends VerificationState {
  const VerificationIdle();
}

final class VerificationVerifying extends VerificationState {
  const VerificationVerifying();
}

final class VerificationSuccess extends VerificationState {
  const VerificationSuccess();
}

final class VerificationError extends VerificationState {
  const VerificationError(this.message);
  final String message;
}

final class VerificationResending extends VerificationState {
  const VerificationResending();
}

final class VerificationResendCooldown extends VerificationState {
  const VerificationResendCooldown(this.secondsRemaining);
  final int secondsRemaining;
}

@riverpod
class VerificationNotifier extends _$VerificationNotifier {
  Timer? _cooldownTimer;

  @override
  FutureOr<VerificationState> build() {
    ref.onDispose(_cancelTimer);
    return const VerificationIdle();
  }

  void _cancelTimer() {
    _cooldownTimer?.cancel();
  }

  Future<void> verify(String email, String code) async {
    state = const AsyncValue.data(VerificationVerifying());

    final IAuthRepository repo = ref.read(authRepositoryProvider);
    try {
      await repo.verifyEmailByCode(email, code);
      // Clear pendingCodeVerification in AuthNotifier BEFORE emitting success,
      // otherwise the router redirect pins us back on /verify-code.
      ref.read(authNotifierProvider.notifier).onVerificationComplete();
      state = const AsyncValue.data(VerificationSuccess());
    } on InvalidCodeFailure {
      state = const AsyncValue.data(
        VerificationError('Код введён неверно. Попробуйте снова.'),
      );
    } on TooManyAttemptsFailure {
      state = const AsyncValue.data(
        VerificationError('Слишком много попыток. Подождите и попробуйте снова.'),
      );
    } on UserNotFoundFailure {
      state = const AsyncValue.data(
        VerificationError('Пользователь не найден.'),
      );
    } catch (e) {
      state = AsyncValue.data(
        VerificationError('Произошла ошибка. Попробуйте снова.'),
      );
    }
  }

  Future<void> resend(String email) async {
    state = const AsyncValue.data(VerificationResending());

    final IAuthRepository repo = ref.read(authRepositoryProvider);
    try {
      await repo.resendVerificationCode(email);
      _startCooldown(60);
    } on ResendCooldownFailure catch (e) {
      _startCooldown(e.retryAfterSeconds > 0 ? e.retryAfterSeconds : 60);
    } on UserNotFoundFailure {
      state = const AsyncValue.data(
        VerificationError('Пользователь не найден.'),
      );
    } catch (e) {
      state = const AsyncValue.data(VerificationIdle());
    }
  }

  void _startCooldown(int seconds) {
    _cooldownTimer?.cancel();
    state = AsyncValue.data(VerificationResendCooldown(seconds));

    int remaining = seconds;
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      remaining--;
      if (remaining <= 0) {
        timer.cancel();
        state = const AsyncValue.data(VerificationIdle());
      } else {
        state = AsyncValue.data(VerificationResendCooldown(remaining));
      }
    });
  }
}
