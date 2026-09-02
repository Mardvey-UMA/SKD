import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors/app_failures.dart';
import '../../features/auth/presentation/providers/auth_provider.dart';
import '../../features/auth/presentation/providers/login_form_provider.dart';
import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../theme/typography.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_input.dart';
import '../../ui/atoms/nf_text.dart';

/// Welcome — entry screen for auth.
///
/// Port of `WelcomeScreen` in `design/reference/mockup/onboarding.jsx`:
/// decorative shapes, brand block (logo + wordmark 46/w800 on desktop via
/// [NFText.display]), email + password inputs (`NFInput`), primary pill
/// CTA «Продолжить» (calls `AuthNotifier.login`), secondary pill «Войти как
/// гость» (routes to `/register`). Replaces the old gradient
/// `LoginScreen` / `RegistrationScreen` pair.
///
/// Responsive: on tablet / desktop the content renders inside a 520-wide
/// centred surface panel (focus mode) regardless of actual viewport.
class WelcomeScreen extends ConsumerStatefulWidget {
  const WelcomeScreen({super.key});

  @override
  ConsumerState<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends ConsumerState<WelcomeScreen> {
  late final TextEditingController _emailController;
  late final TextEditingController _passwordController;

  @override
  void initState() {
    super.initState();
    final initial = ref.read(loginFormNotifierProvider);
    _emailController = TextEditingController(text: initial.email);
    _passwordController = TextEditingController(text: initial.password);
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _submit() {
    final formNotifier = ref.read(loginFormNotifierProvider.notifier);
    final formState = ref.read(loginFormNotifierProvider);
    if (!formNotifier.validate()) return;
    formNotifier.setSubmitting(true);
    ref
        .read(authNotifierProvider.notifier)
        .login(formState.email, formState.password);
  }

  @override
  Widget build(BuildContext context) {
    final formState = ref.watch(loginFormNotifierProvider);
    final authState = ref.watch(authNotifierProvider);
    final formNotifier = ref.read(loginFormNotifierProvider.notifier);

    // Keep controllers in sync with state (restored emails etc.)
    if (formState.email != _emailController.text) {
      _emailController.text = formState.email;
    }
    if (formState.password != _passwordController.text) {
      _passwordController.text = formState.password;
    }

    ref.listen<AsyncValue>(authNotifierProvider, (_, next) {
      next.when(
        data: (_) {},
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

    final Widget content = _WelcomeBody(
      emailController: _emailController,
      passwordController: _passwordController,
      emailError: formState.emailError,
      passwordError: formState.passwordError,
      isLoading: isLoading,
      onEmailChanged: formNotifier.setEmail,
      onPasswordChanged: formNotifier.setPassword,
      onSubmit: _submit,
      onRegister: () => context.go('/register'),
    );

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: _ResponsiveFrame(child: content),
    );
  }

  String _friendlyError(Object error) {
    if (error is AppFailure) {
      final msg = error.message;
      if (error is NetworkFailure) return 'Нет подключения к серверу';
      if (error is AuthFailure) {
        return msg.isNotEmpty && msg != 'Authentication failed.'
            ? msg
            : 'Неверный email или пароль';
      }
      if (error is ServerFailure) return 'Ошибка сервера, попробуйте позже';
      if (error is ConflictFailure) return 'Этот email уже зарегистрирован';
      if (msg.isNotEmpty && msg != 'Validation error.') return msg;
      return 'Проверьте введённые данные';
    }
    return error.toString().replaceAll('Exception: ', '');
  }
}

/// Wraps the onboarding body in a 520-wide surface panel on tablet / desktop
/// (focus mode), renders full-bleed on mobile.
class _ResponsiveFrame extends StatelessWidget {
  const _ResponsiveFrame({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final bp = context.breakpoint;
    if (bp == Breakpoint.mobile) {
      return SafeArea(child: child);
    }
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

class _WelcomeBody extends StatelessWidget {
  const _WelcomeBody({
    required this.emailController,
    required this.passwordController,
    required this.emailError,
    required this.passwordError,
    required this.isLoading,
    required this.onEmailChanged,
    required this.onPasswordChanged,
    required this.onSubmit,
    required this.onRegister,
  });

  final TextEditingController emailController;
  final TextEditingController passwordController;
  final String? emailError;
  final String? passwordError;
  final bool isLoading;
  final ValueChanged<String> onEmailChanged;
  final ValueChanged<String> onPasswordChanged;
  final VoidCallback onSubmit;
  final VoidCallback onRegister;

  @override
  Widget build(BuildContext context) {
    final bp = context.breakpoint;
    final bool mobile = bp == Breakpoint.mobile;

    return Stack(
      clipBehavior: Clip.hardEdge,
      children: <Widget>[
        // Decorative lime blob — top-right.
        Positioned(
          top: -80,
          right: -80,
          width: 260,
          height: 260,
          child: _Blob(color: NFColors.lime.withValues(alpha: 0.45)),
        ),
        // Ink ring — offset below brand so it never crosses the wordmark.
        const Positioned(
          top: 140,
          left: -90,
          width: 160,
          height: 160,
          child: _Ring(color: NFColors.ink),
        ),
        // Accent rotated square — mid-right.
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
              const SizedBox(height: 72),
              NFText.display(
                'Находите\nлучшее\nиз всего.',
                breakpoint: bp,
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
                textInputAction: TextInputAction.done,
                onChanged: onPasswordChanged,
                onSubmitted: (_) => onSubmit(),
                errorText: passwordError,
                autofillHints: const [AutofillHints.password],
              ),
              const SizedBox(height: 20),
              _PrimaryPill(
                label: 'Продолжить',
                isLoading: isLoading,
                onPressed: onSubmit,
              ),
              const SizedBox(height: 10),
              _SecondaryPill(
                label: 'Регистрация',
                onPressed: onRegister,
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

class _Ring extends StatelessWidget {
  const _Ring({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: color),
      ),
    );
  }
}

class _AccentSquare extends StatelessWidget {
  const _AccentSquare();

  @override
  Widget build(BuildContext context) {
    return Transform.rotate(
      angle: 0.7853981633974483, // pi/4 — 45deg
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
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: isLoading ? null : onPressed,
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
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
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
