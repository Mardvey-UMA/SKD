import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/errors/app_failures.dart';
import '../../../../responsive/breakpoint.dart';
import '../../../../responsive/context_ext.dart';
import '../../../../theme/colors.dart';
import '../../../../theme/radii.dart';
import '../../../../theme/typography.dart';
import '../../../../ui/atoms/nf_icon.dart';
import '../../../../ui/atoms/nf_input.dart';
import '../../../../ui/atoms/nf_text.dart';
import '../../../../ui/motion/press_scale.dart';
import '../providers/auth_provider.dart';
import '../providers/registration_form_provider.dart';

/// Registration screen — NF redesign, visually twinned with the welcome
/// (login) screen: flat `NFColors.bg`, brand block, display headline, pill
/// inputs, accent pill CTA, ink outline secondary pill linking to login.
class RegistrationScreen extends ConsumerStatefulWidget {
  const RegistrationScreen({super.key});

  @override
  ConsumerState<RegistrationScreen> createState() =>
      _RegistrationScreenState();
}

class _RegistrationScreenState extends ConsumerState<RegistrationScreen> {
  late final TextEditingController _emailController;
  late final TextEditingController _passwordController;
  late final TextEditingController _confirmController;

  @override
  void initState() {
    super.initState();
    final initial = ref.read(registrationFormNotifierProvider);
    _emailController = TextEditingController(text: initial.email);
    _passwordController = TextEditingController(text: initial.password);
    _confirmController = TextEditingController(text: initial.confirmPassword);
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  void _submit() {
    final notifier = ref.read(registrationFormNotifierProvider.notifier);
    final state = ref.read(registrationFormNotifierProvider);
    if (!notifier.validate()) return;
    notifier.setSubmitting(true);
    ref
        .read(authNotifierProvider.notifier)
        .register(state.email, state.password);
  }

  @override
  Widget build(BuildContext context) {
    final formState = ref.watch(registrationFormNotifierProvider);
    final authState = ref.watch(authNotifierProvider);
    final formNotifier = ref.read(registrationFormNotifierProvider.notifier);

    // Keep controllers in sync with restored state.
    if (formState.email != _emailController.text) {
      _emailController.text = formState.email;
    }
    if (formState.password != _passwordController.text) {
      _passwordController.text = formState.password;
    }
    if (formState.confirmPassword != _confirmController.text) {
      _confirmController.text = formState.confirmPassword;
    }

    ref.listen<AsyncValue>(authNotifierProvider, (previous, next) {
      next.when(
        data: (state) {
          if (state.registrationComplete) {
            context.go('/login');
          } else if (state.pendingCodeVerification) {
            final email = Uri.encodeQueryComponent(state.pendingEmail ?? '');
            context.go('/verify-code?email=$email');
          } else if (state.pendingEmailVerification) {
            final email = Uri.encodeQueryComponent(state.pendingEmail ?? '');
            context.go('/email-verification?email=$email');
          }
        },
        error: (error, _) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(_friendlyError(error)),
              backgroundColor: NFColors.warn,
            ),
          );
          formNotifier.setSubmitting(false);
        },
        loading: () {},
      );
    });

    final bool isLoading = formState.isSubmitting || authState.isLoading;

    final Widget content = _RegistrationBody(
      emailController: _emailController,
      passwordController: _passwordController,
      confirmController: _confirmController,
      emailError: formState.emailError,
      passwordError: formState.passwordError,
      confirmError: formState.confirmPasswordError,
      isLoading: isLoading,
      onEmailChanged: formNotifier.setEmail,
      onPasswordChanged: formNotifier.setPassword,
      onConfirmChanged: formNotifier.setConfirmPassword,
      onSubmit: _submit,
      onGoLogin: () => context.go('/login'),
    );

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: _ResponsiveFrame(child: content),
    );
  }

  String _friendlyError(Object error) {
    if (error is AppFailure) {
      final msg = error.message;
      if (error is ConflictFailure) return 'Этот email уже зарегистрирован';
      if (error is NetworkFailure) return 'Нет подключения к серверу';
      if (error is ServerFailure) return 'Ошибка сервера, попробуйте позже';
      if (error is AuthFailure) return 'Ошибка авторизации';
      if (msg.isNotEmpty && msg != 'Validation error.') return msg;
      return 'Проверьте введённые данные';
    }
    return error
        .toString()
        .replaceAll('Exception: ', '')
        .replaceAll('Instance of ', '');
  }
}

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

class _RegistrationBody extends StatelessWidget {
  const _RegistrationBody({
    required this.emailController,
    required this.passwordController,
    required this.confirmController,
    required this.emailError,
    required this.passwordError,
    required this.confirmError,
    required this.isLoading,
    required this.onEmailChanged,
    required this.onPasswordChanged,
    required this.onConfirmChanged,
    required this.onSubmit,
    required this.onGoLogin,
  });

  final TextEditingController emailController;
  final TextEditingController passwordController;
  final TextEditingController confirmController;
  final String? emailError;
  final String? passwordError;
  final String? confirmError;
  final bool isLoading;
  final ValueChanged<String> onEmailChanged;
  final ValueChanged<String> onPasswordChanged;
  final ValueChanged<String> onConfirmChanged;
  final VoidCallback onSubmit;
  final VoidCallback onGoLogin;

  @override
  Widget build(BuildContext context) {
    final bp = context.breakpoint;
    final bool mobile = bp == Breakpoint.mobile;

    return Stack(
      clipBehavior: Clip.hardEdge,
      children: <Widget>[
        Positioned(
          top: -80,
          right: -80,
          width: 260,
          height: 260,
          child: _Blob(color: NFColors.lime.withValues(alpha: 0.45)),
        ),
        const Positioned(
          top: 160,
          right: 40,
          width: 40,
          height: 40,
          child: _AccentSquare(),
        ),
        SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(22, mobile ? 64 : 36, 22, 30),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              const _BrandBlock(),
              const SizedBox(height: 48),
              NFText.display(
                'Создайте\nаккаунт',
                breakpoint: bp,
              ),
              const SizedBox(height: 12),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 320),
                child: const NFText.body(
                  'Один аккаунт — лента Радара и облачные коллекции.',
                ),
              ),
              const SizedBox(height: 28),
              NFInput(
                controller: emailController,
                placeholder: 'Почта',
                icon: 'user',
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                onChanged: onEmailChanged,
                errorText: emailError,
                autofillHints: const [AutofillHints.email],
              ),
              const SizedBox(height: 10),
              NFInput(
                controller: passwordController,
                placeholder: 'Пароль',
                icon: 'gear',
                obscureText: true,
                textInputAction: TextInputAction.next,
                onChanged: onPasswordChanged,
                errorText: passwordError,
                autofillHints: const [AutofillHints.newPassword],
              ),
              const SizedBox(height: 10),
              NFInput(
                controller: confirmController,
                placeholder: 'Повторите пароль',
                icon: 'gear',
                obscureText: true,
                textInputAction: TextInputAction.done,
                onChanged: onConfirmChanged,
                onSubmitted: (_) => onSubmit(),
                errorText: confirmError,
                autofillHints: const [AutofillHints.newPassword],
              ),
              const SizedBox(height: 20),
              _PrimaryPill(
                label: 'Зарегистрироваться',
                isLoading: isLoading,
                onPressed: onSubmit,
              ),
              const SizedBox(height: 10),
              _SecondaryPill(
                label: 'Уже есть аккаунт — войти',
                onPressed: onGoLogin,
              ),
            ],
          ),
        ),
      ],
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

class _Blob extends StatelessWidget {
  const _Blob({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    );
  }
}

class _AccentSquare extends StatelessWidget {
  const _AccentSquare();

  @override
  Widget build(BuildContext context) {
    return Transform.rotate(
      angle: 0.7853981633974483,
      child: const ColoredBox(color: NFColors.accent),
    );
  }
}

class _PrimaryPill extends StatelessWidget {
  const _PrimaryPill({
    required this.label,
    required this.onPressed,
    required this.isLoading,
  });

  final String label;
  final VoidCallback onPressed;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    return PressScale(
      onTap: isLoading ? () {} : onPressed,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        decoration: BoxDecoration(
          color: NFColors.accent,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            if (isLoading)
              const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    NFColors.accentInk,
                  ),
                ),
              )
            else ...[
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
          ],
        ),
      ),
    );
  }
}

class _SecondaryPill extends StatelessWidget {
  const _SecondaryPill({required this.label, required this.onPressed});

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return PressScale(
      onTap: onPressed,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 15),
        decoration: BoxDecoration(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: NFColors.ink, width: 1.5),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: NFTypography.h2.copyWith(
            fontSize: 15,
            fontWeight: FontWeight.w700,
            letterSpacing: 0,
            color: NFColors.ink,
          ),
        ),
      ),
    );
  }
}
