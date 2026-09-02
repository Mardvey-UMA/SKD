import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../shared/widgets/custom_text_field.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../models/login_form_state.dart';
import '../providers/auth_provider.dart';
import '../providers/login_form_provider.dart';

/// Login screen — gradient hero bg, glass pill, token-aligned inputs & buttons.
class LoginScreen extends ConsumerWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final formState = ref.watch(loginFormNotifierProvider);
    final authState = ref.watch(authNotifierProvider);
    final formNotifier = ref.read(loginFormNotifierProvider.notifier);
    final authNotifier = ref.read(authNotifierProvider.notifier);

    ref.listen<AsyncValue>(authNotifierProvider, (previous, next) {
      next.when(
        data: (_) {},
        error: (error, stackTrace) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(_friendlyError(error)),
              backgroundColor: AppColors.error,
            ),
          );
          formNotifier.setSubmitting(false);
        },
        loading: () {},
      );
    });

    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        // §5.3 gradient.loginHero
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment(0, -1),
            end: Alignment(0.6, 1),
            transform: GradientRotation(160 * 3.14159 / 180),
            colors: [
              Color(0xFFEEF1FF),
              Color(0xFFF7ECFF),
              Color(0xFFFFE8F1),
            ],
            stops: [0.0, 0.55, 1.0],
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            child: Column(
              children: [
                // Glass pill «Радар» (§ glass capsule)
                ClipRRect(
                  borderRadius: BorderRadius.circular(9999),
                  child: BackdropFilter(
                    filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.68),
                        borderRadius: BorderRadius.circular(9999),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.7),
                          width: 1,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF0F1828).withValues(alpha: 0.04),
                            offset: const Offset(0, 1),
                            blurRadius: 2,
                          ),
                          BoxShadow(
                            color: const Color(0xFF0F1828).withValues(alpha: 0.03),
                            offset: const Offset(0, 2),
                            blurRadius: 6,
                          ),
                        ],
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: const [
                          Icon(
                            Icons.radar,
                            color: Color(0xFF3D5BFF),
                            size: 18,
                          ),
                          SizedBox(width: 6),
                          Text(
                            'Радар',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF3D5BFF),
                              letterSpacing: 0.002 * 13,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 48),

                // Hero title — display-l §2.2: 28px / 36 lh / 700 / -0.013 ls
                const Text(
                  'Открывайте контент,\nкоторый вам понравится ✨',
                  textAlign: TextAlign.center,
                  maxLines: 3,
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF0E1525), // text.primary = neutral-900
                    height: 36 / 28,
                    letterSpacing: -0.013 * 28,
                  ),
                ),
                // xs gap (8) between title and subtitle
                const SizedBox(height: 8),

                // Subtitle — body-l §2.2: 16px / 24 lh / 400
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 320),
                  child: const Text(
                    'Персональная лента: технологии, игры,\nлонгриды и многое другое.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w400,
                      color: Color(0xFF485063), // text.secondary = neutral-600
                      height: 24 / 16,
                    ),
                  ),
                ),
                // xl2 gap (32) between subtitle and form
                const SizedBox(height: 32),

                // Email Field
                _EmailField(formState: formState, formNotifier: formNotifier),
                // md gap (16) between fields
                const SizedBox(height: 16),

                // Password Field
                _PasswordField(
                  formState: formState,
                  formNotifier: formNotifier,
                  onSubmitted: () => _handleLogin(
                    context,
                    ref,
                    formState,
                    formNotifier,
                    authNotifier,
                  ),
                ),
                const SizedBox(height: 12),

                // Forgot Password — bodyS 600 interactive.primary, min tap 40px
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () => context.go('/forgot-password'),
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 8,
                      ),
                      minimumSize: const Size(0, 40),
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text(
                      'Забыли пароль?',
                      style: TextStyle(
                        color: Color(0xFF3D5BFF), // interactive.primary
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                        letterSpacing: 0.002 * 13,
                      ),
                    ),
                  ),
                ),
                // xl gap (24) between form and primary button
                const SizedBox(height: 24),

                PrimaryButton(
                  text: 'Войти',
                  isLoading: formState.isSubmitting || authState.isLoading,
                  onPressed: () => _handleLogin(
                    context,
                    ref,
                    formState,
                    formNotifier,
                    authNotifier,
                  ),
                ),
                // sm gap (12) between buttons
                const SizedBox(height: 12),

                SecondaryButton(
                  text: 'Регистрация',
                  onPressed: () => context.go('/register'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _handleLogin(
    BuildContext context,
    WidgetRef ref,
    LoginFormState formState,
    LoginFormNotifier formNotifier,
    AuthNotifier authNotifier,
  ) {
    if (!formNotifier.validate()) return;
    formNotifier.setSubmitting(true);
    authNotifier.login(formState.email, formState.password);
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

class _EmailField extends ConsumerStatefulWidget {
  const _EmailField({required this.formState, required this.formNotifier});

  final LoginFormState formState;
  final LoginFormNotifier formNotifier;

  @override
  ConsumerState<_EmailField> createState() => _EmailFieldState();
}

class _EmailFieldState extends ConsumerState<_EmailField> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.formState.email);
  }

  @override
  void didUpdateWidget(covariant _EmailField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.formState.email != _controller.text) {
      _controller.text = widget.formState.email;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CustomTextField(
      label: 'Эл. почта',
      controller: _controller,
      icon: Icons.email_outlined,
      keyboardType: TextInputType.emailAddress,
      errorText: widget.formState.emailError,
      onChanged: widget.formNotifier.setEmail,
      textInputAction: TextInputAction.next,
    );
  }
}

class _PasswordField extends ConsumerStatefulWidget {
  const _PasswordField({
    required this.formState,
    required this.formNotifier,
    required this.onSubmitted,
  });

  final LoginFormState formState;
  final LoginFormNotifier formNotifier;
  final VoidCallback onSubmitted;

  @override
  ConsumerState<_PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends ConsumerState<_PasswordField> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.formState.password);
  }

  @override
  void didUpdateWidget(covariant _PasswordField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.formState.password != _controller.text) {
      _controller.text = widget.formState.password;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CustomTextField(
      label: 'Пароль',
      controller: _controller,
      icon: Icons.lock_outline,
      obscureText: widget.formState.obscurePassword,
      errorText: widget.formState.passwordError,
      onChanged: widget.formNotifier.setPassword,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => widget.onSubmitted(),
    );
  }
}
