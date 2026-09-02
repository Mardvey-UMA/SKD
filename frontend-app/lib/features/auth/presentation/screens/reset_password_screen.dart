import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/validators/form_validators.dart';
import '../../../../shared/widgets/custom_text_field.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../providers/auth_repository_provider.dart';

/// Screen for submitting a new password using a reset token.
class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({super.key, this.token});

  /// Token from deep link or manual input.
  final String? token;

  @override
  ConsumerState<ResetPasswordScreen> createState() =>
      _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  late final TextEditingController _tokenController;
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();

  String? _tokenError;
  String? _passwordError;
  String? _confirmError;
  bool _isLoading = false;
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    _tokenController = TextEditingController(text: widget.token ?? '');
  }

  @override
  void dispose() {
    _tokenController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final token = _tokenController.text.trim();
    final password = _passwordController.text;
    final confirm = _confirmController.text;

    final tokenError = token.isEmpty ? 'Введите токен' : null;
    final passwordError = FormValidators.validatePassword(password);
    final confirmError = FormValidators.validatePasswordMatch(
      password,
      confirm,
    );

    setState(() {
      _tokenError = tokenError;
      _passwordError = passwordError;
      _confirmError = confirmError;
    });

    if (tokenError != null || passwordError != null || confirmError != null) {
      return;
    }

    setState(() => _isLoading = true);

    try {
      final repo = ref.read(authRepositoryProvider);
      await repo.resetPassword(token, password);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Пароль успешно изменён. Войдите в аккаунт.'),
            backgroundColor: Colors.green,
          ),
        );
        context.go('/login');
      }
    } on AppFailure catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.message), backgroundColor: AppColors.error),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Произошла ошибка. Попробуйте снова.'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios, color: AppColors.textPrimary),
          onPressed: () => context.go('/login'),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Сброс пароля',
              style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w800,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Введите токен из письма и новый пароль.',
              style: TextStyle(
                fontSize: 16,
                color: AppColors.textSecondary,
                height: 1.5,
              ),
            ),
            const SizedBox(height: 32),
            if (widget.token == null) ...[
              CustomTextField(
                label: 'Токен сброса',
                controller: _tokenController,
                icon: Icons.key_outlined,
                errorText: _tokenError,
                onChanged: (_) => setState(() => _tokenError = null),
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 16),
            ],
            CustomTextField(
              label: 'Новый пароль',
              controller: _passwordController,
              icon: Icons.lock_outline,
              obscureText: _obscurePassword,
              errorText: _passwordError,
              onChanged: (_) => setState(() => _passwordError = null),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 16),
            CustomTextField(
              label: 'Подтвердите новый пароль',
              controller: _confirmController,
              icon: Icons.lock_outline,
              obscureText: _obscurePassword,
              errorText: _confirmError,
              onChanged: (_) => setState(() => _confirmError = null),
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () =>
                    setState(() => _obscurePassword = !_obscurePassword),
                child: Text(
                  _obscurePassword ? 'Показать пароль' : 'Скрыть пароль',
                  style: const TextStyle(color: AppColors.primary),
                ),
              ),
            ),
            const SizedBox(height: 24),
            PrimaryButton(
              text: 'Сбросить пароль',
              isLoading: _isLoading,
              onPressed: _submit,
            ),
          ],
        ),
      ),
    );
  }
}
