import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../responsive/breakpoint.dart';
import '../../../../responsive/context_ext.dart';
import '../../../../theme/colors.dart';
import '../../../../theme/radii.dart';
import '../../../../theme/typography.dart';
import '../../../../ui/atoms/nf_icon.dart';
import '../../../../ui/atoms/nf_otp_input.dart';
import '../../../../ui/atoms/nf_text.dart';
import '../../../../ui/motion/press_scale.dart';
import '../providers/verification_provider.dart';

/// Email verification code screen — NF design, optimised for fast typing.
///
/// Matches the welcome screen visual language: flat `NFColors.bg`, brand
/// block, display headline, pill CTA, textual resend link. Everything
/// glass-blur / aurora-gradient has been removed — they make Flutter Web
/// stutter during per-keystroke OTP input.
///
/// Repaint surface is scoped:
/// * OTP cells rebuild only on controller ticks (see [NFOtpInput]).
/// * Countdown timer lives in its own [ValueListenableBuilder] so the
///   1-Hz tick never touches the header, CTA or resend row.
/// * CTA is kept in a separate [ConsumerWidget] so verification-state
///   transitions don't invalidate the timer / OTP cells.
class EmailVerificationCodeScreen extends ConsumerStatefulWidget {
  const EmailVerificationCodeScreen({super.key, required this.email});

  final String email;

  @override
  ConsumerState<EmailVerificationCodeScreen> createState() =>
      _EmailVerificationCodeScreenState();
}

class _EmailVerificationCodeScreenState
    extends ConsumerState<EmailVerificationCodeScreen> {
  /// Current OTP digits — owned here so both CTA-enabled logic and
  /// `_onVerify` can read it without touching the OTP cells.
  final ValueNotifier<String> _code = ValueNotifier<String>('');

  /// Countdown timer; `0` means «expired, resend allowed».
  final ValueNotifier<int> _secondsLeft = ValueNotifier<int>(238);
  Timer? _codeTimer;

  @override
  void initState() {
    super.initState();
    _startCodeTimer();
  }

  @override
  void dispose() {
    _codeTimer?.cancel();
    _code.dispose();
    _secondsLeft.dispose();
    super.dispose();
  }

  void _startCodeTimer() {
    _codeTimer?.cancel();
    _codeTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      final next = _secondsLeft.value - 1;
      if (next < 0) {
        _codeTimer?.cancel();
        return;
      }
      _secondsLeft.value = next;
      if (next == 0) _codeTimer?.cancel();
    });
  }

  void _onCodeChanged(String value) {
    _code.value = value;
  }

  void _onCodeCompleted(String value) {
    _code.value = value;
    _submitIfReady(value);
  }

  Future<void> _submitIfReady(String value) async {
    if (value.length != 6) return;
    await ref
        .read(verificationNotifierProvider.notifier)
        .verify(widget.email, value);
  }

  Future<void> _onVerify() => _submitIfReady(_code.value);

  Future<void> _onResend() async {
    await ref
        .read(verificationNotifierProvider.notifier)
        .resend(widget.email);
    _secondsLeft.value = 238;
    _startCodeTimer();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<AsyncValue<VerificationState>>(
      verificationNotifierProvider,
      (_, next) {
        if (next.valueOrNull is VerificationSuccess) {
          context.go('/login');
        }
      },
    );

    final Widget content = _VerificationBody(
      email: widget.email,
      code: _code,
      secondsLeft: _secondsLeft,
      onCodeChanged: _onCodeChanged,
      onCodeCompleted: _onCodeCompleted,
      onVerify: _onVerify,
      onResend: _onResend,
    );

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: _ResponsiveFrame(child: content),
    );
  }
}

/// Mirrors welcome-screen frame: full-bleed on mobile, 520-wide focus
/// panel on tablet / desktop.
class _ResponsiveFrame extends StatelessWidget {
  const _ResponsiveFrame({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final bp = context.breakpoint;
    if (bp == Breakpoint.mobile) return SafeArea(child: child);
    return SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Container(
            decoration: BoxDecoration(
              color: NFColors.bg,
              borderRadius: BorderRadius.circular(NFRadii.radiusLg),
              border: Border.all(color: NFColors.hairline),
            ),
            clipBehavior: Clip.antiAlias,
            child: child,
          ),
        ),
      ),
    );
  }
}

class _VerificationBody extends ConsumerWidget {
  const _VerificationBody({
    required this.email,
    required this.code,
    required this.secondsLeft,
    required this.onCodeChanged,
    required this.onCodeCompleted,
    required this.onVerify,
    required this.onResend,
  });

  final String email;
  final ValueNotifier<String> code;
  final ValueNotifier<int> secondsLeft;
  final ValueChanged<String> onCodeChanged;
  final ValueChanged<String> onCodeCompleted;
  final VoidCallback onVerify;
  final VoidCallback onResend;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bp = context.breakpoint;
    final bool mobile = bp == Breakpoint.mobile;

    return SingleChildScrollView(
      padding: EdgeInsets.fromLTRB(22, mobile ? 64 : 36, 22, 30),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          const _BrandBlock(),
          const SizedBox(height: 48),

          NFText.display('Подтвердите\nпочту', breakpoint: bp),
          const SizedBox(height: 12),

          _EmailSubtitle(email: email),
          const SizedBox(height: 20),

          const _TemporaryCodeNotice(),
          const SizedBox(height: 20),

          // Timer — isolated so 1-Hz tick doesn't touch OTP cells.
          _TimerLine(secondsLeft: secondsLeft),
          const SizedBox(height: 16),

          // OTP — consumes verification error state so cells colour-flip.
          _OtpSection(
            onChanged: onCodeChanged,
            onCompleted: onCodeCompleted,
          ),
          const SizedBox(height: 10),

          _ErrorLine(),
          const SizedBox(height: 20),

          _SubmitButton(code: code, onVerify: onVerify),
          const SizedBox(height: 14),

          _ResendRow(secondsLeft: secondsLeft, onResend: onResend),
        ],
      ),
    );
  }
}

class _BrandBlock extends StatelessWidget {
  const _BrandBlock();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            color: NFColors.ink,
            borderRadius: BorderRadius.circular(9),
          ),
          alignment: Alignment.center,
          child: Stack(
            alignment: Alignment.center,
            children: <Widget>[
              Container(
                margin: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: NFColors.lime, width: 1.2),
                ),
              ),
              Container(
                width: 3,
                height: 3,
                decoration: const BoxDecoration(
                  color: NFColors.lime,
                  shape: BoxShape.circle,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 10),
        Text(
          'Радар',
          style: NFTypography.h2.copyWith(
            fontSize: 20,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.8,
            color: NFColors.ink,
          ),
        ),
      ],
    );
  }
}

class _EmailSubtitle extends StatelessWidget {
  const _EmailSubtitle({required this.email});

  final String email;

  @override
  Widget build(BuildContext context) {
    final base = NFTypography.body.copyWith(color: NFColors.mute);
    return RichText(
      text: TextSpan(
        style: base,
        children: <InlineSpan>[
          const TextSpan(text: 'Код отправлен на '),
          TextSpan(
            text: email,
            style: base.copyWith(
              color: NFColors.ink,
              fontWeight: FontWeight.w700,
            ),
          ),
          const TextSpan(text: '.'),
        ],
      ),
    );
  }
}

class _TemporaryCodeNotice extends StatelessWidget {
  const _TemporaryCodeNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: NFColors.warn.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(NFRadii.radiusSm),
        border: Border.all(color: NFColors.warn, width: 1.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text(
            'ИЗВИНИТЕ ЗА НЕУДОБСТВА',
            textAlign: TextAlign.center,
            style: NFTypography.h2.copyWith(
              fontSize: 15,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.6,
              color: NFColors.warn,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'ВРЕМЕННО ДЛЯ ВЕРИФИКАЦИИ\nИСПОЛЬЗУЕТСЯ КОД',
            textAlign: TextAlign.center,
            style: NFTypography.body.copyWith(
              fontWeight: FontWeight.w700,
              color: NFColors.ink,
              height: 1.3,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '000000',
            textAlign: TextAlign.center,
            style: NFTypography.mono.copyWith(
              fontSize: 26,
              fontWeight: FontWeight.w800,
              letterSpacing: 6,
              color: NFColors.ink,
            ),
          ),
        ],
      ),
    );
  }
}

class _TimerLine extends StatelessWidget {
  const _TimerLine({required this.secondsLeft});

  final ValueNotifier<int> secondsLeft;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: secondsLeft,
      builder: (context, secs, _) {
        final m = (secs ~/ 60).toString().padLeft(2, '0');
        final s = (secs % 60).toString().padLeft(2, '0');
        final expired = secs == 0;
        return Text(
          expired ? 'Срок кода истёк' : 'Код действителен $m:$s',
          style: NFTypography.mono.copyWith(
            color: expired ? NFColors.warn : NFColors.mute,
          ),
        );
      },
    );
  }
}

class _OtpSection extends ConsumerWidget {
  const _OtpSection({required this.onChanged, required this.onCompleted});

  final ValueChanged<String> onChanged;
  final ValueChanged<String> onCompleted;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final verification = ref.watch(
      verificationNotifierProvider.select((v) => v.valueOrNull),
    );
    final hasError = verification is VerificationError;

    return NFOtpInput(
      length: 6,
      onChanged: onChanged,
      onCompleted: onCompleted,
      hasError: hasError,
    );
  }
}

class _ErrorLine extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final verification = ref.watch(
      verificationNotifierProvider.select((v) => v.valueOrNull),
    );
    if (verification is! VerificationError) {
      return const SizedBox.shrink();
    }
    return Text(
      verification.message,
      textAlign: TextAlign.center,
      style: NFTypography.body.copyWith(color: NFColors.warn),
    );
  }
}

class _SubmitButton extends ConsumerWidget {
  const _SubmitButton({required this.code, required this.onVerify});

  final ValueNotifier<String> code;
  final VoidCallback onVerify;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final verification = ref.watch(
      verificationNotifierProvider.select((v) => v.valueOrNull),
    );
    final isVerifying = verification is VerificationVerifying;
    final isError = verification is VerificationError;

    return ValueListenableBuilder<String>(
      valueListenable: code,
      builder: (context, value, _) {
        final bool canSubmit = value.length == 6 && !isVerifying;
        final String label = isError ? 'Попробовать снова' : 'Подтвердить';
        return Opacity(
          opacity: canSubmit ? 1.0 : 0.55,
          child: PressScale(
            onTap: canSubmit ? onVerify : () {},
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
              decoration: BoxDecoration(
                color: NFColors.accent,
                borderRadius: BorderRadius.circular(999),
              ),
              alignment: Alignment.center,
              child: isVerifying
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(
                          NFColors.accentInk,
                        ),
                      ),
                    )
                  : Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: <Widget>[
                        Text(
                          label,
                          style: NFTypography.h2.copyWith(
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 0,
                            color: NFColors.accentInk,
                          ),
                        ),
                        const SizedBox(width: 8),
                        const NFIcon(
                          'arrow-up-right',
                          size: 15,
                          color: NFColors.accentInk,
                        ),
                      ],
                    ),
            ),
          ),
        );
      },
    );
  }
}

class _ResendRow extends ConsumerWidget {
  const _ResendRow({required this.secondsLeft, required this.onResend});

  final ValueNotifier<int> secondsLeft;
  final VoidCallback onResend;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final verification = ref.watch(
      verificationNotifierProvider.select((v) => v.valueOrNull),
    );

    int? cooldown;
    bool resending = false;
    if (verification is VerificationResendCooldown) {
      cooldown = verification.secondsRemaining;
    } else if (verification is VerificationResending) {
      resending = true;
    }

    return ValueListenableBuilder<int>(
      valueListenable: secondsLeft,
      builder: (context, secs, _) {
        final bool allowResend =
            !resending && cooldown == null && secs == 0;
        final String label;
        if (resending) {
          label = 'Отправляем…';
        } else if (cooldown != null) {
          label = 'Повторно через $cooldown с';
        } else {
          label = 'Отправить повторно';
        }
        final Color labelColor =
            allowResend ? NFColors.accent : NFColors.mute2;

        return Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(
              'Не пришёл код?  ',
              style: NFTypography.body.copyWith(color: NFColors.mute),
            ),
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: allowResend ? onResend : null,
              child: Text(
                label,
                style: NFTypography.body.copyWith(
                  color: labelColor,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
