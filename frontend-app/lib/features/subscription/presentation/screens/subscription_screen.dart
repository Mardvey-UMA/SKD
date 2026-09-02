import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/models/subscription_status.dart';
import '../providers/subscription_provider.dart';

class SubscriptionScreen extends ConsumerStatefulWidget {
  const SubscriptionScreen({super.key});

  @override
  ConsumerState<SubscriptionScreen> createState() => _SubscriptionScreenState();
}

class _SubscriptionScreenState extends ConsumerState<SubscriptionScreen> {
  @override
  void initState() {
    super.initState();
    // Force a refresh on every entry to the screen so the UI always sees the
    // latest tier (e.g. after payment return or token refresh).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ref.read(subscriptionNotifierProvider.notifier).refresh();
    });
  }

  @override
  Widget build(BuildContext context) {
    final statusAsync = ref.watch(subscriptionNotifierProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
          onPressed: () {
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/shell/settings');
            }
          },
        ),
        title: const Text(
          'Подписка',
          style: TextStyle(
            color: AppColors.textPrimary,
            fontWeight: FontWeight.w600,
            fontSize: 17,
          ),
        ),
        centerTitle: true,
      ),
      body: statusAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline, color: AppColors.error, size: 48),
              const SizedBox(height: 12),
              Text(
                'Не удалось загрузить подписку',
                style: const TextStyle(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () =>
                    ref.read(subscriptionNotifierProvider.notifier).refresh(),
                child: const Text('Повторить'),
              ),
            ],
          ),
        ),
        data: (status) =>
            status.isPremium ? _PremiumView(status: status) : const _FreeView(),
      ),
    );
  }
}

// ── Free plan view ──────────────────────────────────────────────────────────

class _FreeView extends ConsumerWidget {
  const _FreeView();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Hero section
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppColors.gradientStart, AppColors.gradientEnd],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Column(
              children: [
                Icon(Icons.star_rounded, color: Colors.white, size: 48),
                SizedBox(height: 12),
                Text(
                  'Перейти на Премиум',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                  ),
                  textAlign: TextAlign.center,
                ),
                SizedBox(height: 8),
                Text(
                  'Безлимитный контент, без рекламы и персональные рекомендации',
                  style: TextStyle(color: Colors.white70, fontSize: 14),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          // Feature list
          const _FeatureRow(
            icon: Icons.all_inclusive,
            text: 'Безлимит статей в день',
          ),
          const _FeatureRow(icon: Icons.block, text: 'Без рекламы'),
          const _FeatureRow(icon: Icons.tune, text: 'Расширенная персонализация'),
          const _FeatureRow(
            icon: Icons.download_outlined,
            text: 'Офлайн-чтение',
          ),
          const SizedBox(height: 24),
          // Monthly plan
          _PlanCard(
            title: 'Ежемесячно',
            price: '₽299',
            period: 'в месяц',
            planId: 'premium_monthly',
            isRecommended: false,
          ),
          const SizedBox(height: 12),
          // Yearly plan
          _PlanCard(
            title: 'Ежегодно',
            price: '₽2990',
            period: 'в год · экономия 17%',
            planId: 'premium_yearly',
            isRecommended: true,
          ),
          const SizedBox(height: 16),
          Text(
            'Отмена в любое время. Оплата через ЮKassa.',
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12, color: AppColors.textHint),
          ),
        ],
      ),
    );
  }
}

class _FeatureRow extends StatelessWidget {
  const _FeatureRow({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: const BoxDecoration(
              color: AppColors.iconBgBlue,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 16, color: AppColors.primary),
          ),
          const SizedBox(width: 12),
          Text(
            text,
            style: const TextStyle(fontSize: 15, color: AppColors.textPrimary),
          ),
        ],
      ),
    );
  }
}

class _PlanCard extends ConsumerStatefulWidget {
  const _PlanCard({
    required this.title,
    required this.price,
    required this.period,
    required this.planId,
    required this.isRecommended,
  });

  final String title;
  final String price;
  final String period;
  final String planId;
  final bool isRecommended;

  @override
  ConsumerState<_PlanCard> createState() => _PlanCardState();
}

class _PlanCardState extends ConsumerState<_PlanCard> {
  bool _isLoading = false;

  Future<void> _onTap() async {
    setState(() => _isLoading = true);
    try {
      final result = await ref
          .read(subscriptionNotifierProvider.notifier)
          .checkout(widget.planId);

      final uri = Uri.parse(result.confirmationUrl);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Не удалось открыть страницу оплаты')),
          );
        }
        return;
      }

      if (!mounted) return;
      // After opening YooKassa — show waiting dialog that polls the backend
      // until the webhook flips the user to premium, then refreshes JWT.
      await _PaymentWaitDialog.show(context, ref);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Error: ${e.toString()}')));
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Container(
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(12),
            border: widget.isRecommended
                ? Border.all(color: AppColors.primary, width: 2)
                : null,
          ),
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: _isLoading ? null : _onTap,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.title,
                            style: const TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            widget.period,
                            style: const TextStyle(
                              fontSize: 13,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (_isLoading)
                      const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    else
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            widget.price,
                            style: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.w700,
                              color: AppColors.primary,
                            ),
                          ),
                          const Icon(
                            Icons.chevron_right,
                            color: AppColors.textHint,
                            size: 20,
                          ),
                        ],
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
        if (widget.isRecommended)
          Positioned(
            top: -1,
            right: 16,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: const BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.vertical(bottom: Radius.circular(6)),
              ),
              child: const Text(
                'ВЫГОДНО',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

// ── Premium plan view ───────────────────────────────────────────────────────

class _PremiumView extends ConsumerStatefulWidget {
  const _PremiumView({required this.status});

  final SubscriptionStatus status;

  @override
  ConsumerState<_PremiumView> createState() => _PremiumViewState();
}

class _PremiumViewState extends ConsumerState<_PremiumView> {
  bool _isCancelling = false;

  String _formatDate(DateTime? dt) {
    if (dt == null) return 'N/A';
    return '${dt.day.toString().padLeft(2, '0')}.${dt.month.toString().padLeft(2, '0')}.${dt.year}';
  }

  String _planName(String? planId) {
    return switch (planId) {
      'premium_monthly' => 'Премиум (месяц)',
      'premium_yearly' => 'Премиум (год)',
      _ => 'Премиум',
    };
  }

  Future<void> _cancelAutoRenew() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Отменить автопродление?'),
        content: const Text(
          'Премиум-доступ останется активным до даты окончания, но не будет продлён автоматически.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Оставить Премиум'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Отменить продление',
              style: TextStyle(color: AppColors.error),
            ),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() => _isCancelling = true);
    try {
      await ref.read(subscriptionNotifierProvider.notifier).cancel();
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Автопродление отменено')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Error: ${e.toString()}')));
      }
    } finally {
      if (mounted) setState(() => _isCancelling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = widget.status;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Status card
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppColors.gradientStart, AppColors.gradientEnd],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              children: [
                const Icon(
                  Icons.verified_rounded,
                  color: Colors.white,
                  size: 48,
                ),
                const SizedBox(height: 12),
                const Text(
                  'Премиум активен',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  _planName(status.planId),
                  style: const TextStyle(color: Colors.white70, fontSize: 14),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          // Details
          _DetailCard(
            children: [
              _DetailRow(label: 'План', value: _planName(status.planId)),
              _DetailRow(label: 'Статус', value: status.status.toUpperCase()),
              _DetailRow(
                label: 'Истекает',
                value: _formatDate(status.expiresAt),
              ),
              _DetailRow(
                label: 'Автопродление',
                value: status.autoRenew ? 'Вкл' : 'Выкл',
              ),
            ],
          ),
          const SizedBox(height: 20),
          if (status.autoRenew)
            OutlinedButton(
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.error,
                side: const BorderSide(color: AppColors.error),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              onPressed: _isCancelling ? null : _cancelAutoRenew,
              child: _isCancelling
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AppColors.error,
                      ),
                    )
                  : const Text('Отменить автопродление'),
            ),
        ],
      ),
    );
  }
}

class _DetailCard extends StatelessWidget {
  const _DetailCard({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(children: children),
    );
  }
}

// ── Payment wait dialog ─────────────────────────────────────────────────────

class _PaymentWaitDialog extends ConsumerStatefulWidget {
  const _PaymentWaitDialog();

  static Future<void> show(BuildContext context, WidgetRef ref) {
    return showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const _PaymentWaitDialog(),
    );
  }

  @override
  ConsumerState<_PaymentWaitDialog> createState() => _PaymentWaitDialogState();
}

class _PaymentWaitDialogState extends ConsumerState<_PaymentWaitDialog> {
  bool _checking = false;
  String? _errorText;

  @override
  void initState() {
    super.initState();
    // Kick off automatic polling in the background. If it succeeds while the
    // dialog is still open, close it and show a snackbar.
    WidgetsBinding.instance.addPostFrameCallback((_) => _startPolling());
  }

  Future<void> _startPolling() async {
    final ok = await ref
        .read(subscriptionNotifierProvider.notifier)
        .pollUntilPremium();
    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Премиум активирован!')),
      );
    }
  }

  Future<void> _manualCheck() async {
    setState(() {
      _checking = true;
      _errorText = null;
    });
    final ok = await ref
        .read(subscriptionNotifierProvider.notifier)
        .verifyAfterReturn();
    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Премиум активирован!')),
      );
    } else {
      setState(() {
        _checking = false;
        _errorText = 'Оплата ещё не подтверждена. Попробуйте ещё раз.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Ожидаем подтверждение оплаты'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Завершите оплату в открывшейся вкладке. '
            'Статус обновится автоматически, как только мы получим подтверждение.',
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Text(
                  'Проверяем статус…',
                  style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
                ),
              ),
            ],
          ),
          if (_errorText != null) ...[
            const SizedBox(height: 8),
            Text(
              _errorText!,
              style: const TextStyle(fontSize: 12, color: AppColors.error),
            ),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: _checking ? null : () => Navigator.of(context).pop(),
          child: const Text('Закрыть'),
        ),
        FilledButton(
          onPressed: _checking ? null : _manualCheck,
          child: const Text('Я оплатил'),
        ),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(
              fontSize: 15,
              color: AppColors.textSecondary,
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}
