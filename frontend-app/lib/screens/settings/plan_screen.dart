import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../features/subscription/domain/models/subscription_status.dart';
import '../../features/subscription/presentation/providers/subscription_provider.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/icon_btn.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';

/// Plan screen (Premium upsell + comparison + CTA).
///
/// Mirrors `PlanScreen` in `design/reference/mockup/screens.jsx`. Mounted
/// at the existing `/subscription` route. Free users see the billing cycle
/// toggle + price card + feature list + comparison table + «Оформить» CTA
/// that triggers the YooKassa checkout flow (preserved from the previous
/// `SubscriptionScreen` impl). Premium users see a «Управлять подпиской»
/// row and the next billing date — no price card or CTA.
class PlanScreen extends ConsumerStatefulWidget {
  const PlanScreen({super.key});

  @override
  ConsumerState<PlanScreen> createState() => _PlanScreenState();
}

class _PlanScreenState extends ConsumerState<PlanScreen> {
  _Cycle _cycle = _Cycle.year;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ref.read(subscriptionNotifierProvider.notifier).refresh();
    });
  }

  @override
  Widget build(BuildContext context) {
    final statusAsync = ref.watch(subscriptionNotifierProvider);

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: statusAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => _ErrorView(
            onRetry: () =>
                ref.read(subscriptionNotifierProvider.notifier).refresh(),
          ),
          data: (status) => _PlanBody(
            status: status,
            cycle: _cycle,
            onCycleChanged: (c) => setState(() => _cycle = c),
          ),
        ),
      ),
    );
  }
}

enum _Cycle { month, year }

extension on _Cycle {
  String get label => switch (this) {
        _Cycle.month => 'Помесячно',
        _Cycle.year => 'Годовой',
      };

  String get price => switch (this) {
        _Cycle.month => '299 ₽',
        _Cycle.year => '2 490 ₽',
      };

  String get per => switch (this) {
        _Cycle.month => 'в месяц',
        _Cycle.year => 'в год',
      };

  String get note => switch (this) {
        _Cycle.month => 'Списание каждый месяц · отмена в любой момент',
        _Cycle.year => '207 ₽/мес · экономия 30% против помесячной оплаты',
      };

  String get planId => switch (this) {
        _Cycle.month => 'premium_monthly',
        _Cycle.year => 'premium_yearly',
      };

  String get perCta => switch (this) {
        _Cycle.month => '/ мес',
        _Cycle.year => '/ год',
      };
}

class _PlanBody extends ConsumerWidget {
  const _PlanBody({
    required this.status,
    required this.cycle,
    required this.onCycleChanged,
  });

  final SubscriptionStatus status;
  final _Cycle cycle;
  final ValueChanged<_Cycle> onCycleChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bool isPremium = status.isPremium;

    return ListView(
      padding: const EdgeInsets.fromLTRB(0, 14, 0, 170),
      children: <Widget>[
        // Header: back + kicker
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
          child: Row(
            children: <Widget>[
              IconBtn(
                iconName: 'back',
                onTap: () {
                  if (context.canPop()) {
                    context.pop();
                  } else {
                    context.go('/shell/settings');
                  }
                },
                semanticLabel: 'Назад',
              ),
              const SizedBox(width: 10),
              Expanded(
                child: NFText.mono(
                  'ТАРИФ · ${isPremium ? 'PREMIUM' : 'FREE'}',
                ),
              ),
            ],
          ),
        ),

        // Hero card
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 8, 14, 0),
          child: _HeroCard(),
        ),

        // Cycle toggle + price card (free only)
        if (!isPremium) ...<Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 18, 14, 0),
            child: _CycleToggle(
              cycle: cycle,
              onChanged: onCycleChanged,
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 10, 14, 0),
            child: _PriceCard(cycle: cycle),
          ),
        ],

        // What's included
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 22, 14, 0),
          child: _FeaturesList(),
        ),

        // Comparison
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 22, 14, 0),
          child: _ComparisonTable(),
        ),

        const SizedBox(height: 20),

        // CTA
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 0, 14, 0),
          child: _CtaCard(status: status, cycle: cycle),
        ),
      ],
    );
  }
}

class _HeroCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: NFRadii.brLg,
      child: Container(
        color: NFColors.ink,
        padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
        child: Stack(
          children: <Widget>[
            Positioned(
              top: -30,
              right: -40,
              child: Container(
                width: 180,
                height: 180,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: <Color>[
                      NFColors.lime.withValues(alpha: 0.35),
                      NFColors.lime.withValues(alpha: 0.0),
                    ],
                    stops: const <double>[0.0, 0.7],
                  ),
                ),
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Row(
                  children: const <Widget>[
                    NFIcon('star', size: 14, color: NFColors.lime),
                    SizedBox(width: 8),
                    NFText.mono('RADAR PREMIUM', color: NFColors.lime),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'Читайте то,\nчто важно вам',
                  style: TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 32,
                    height: 1.0,
                    letterSpacing: -1.2,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 10),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 300),
                  child: Text(
                    'Premium снимает лимиты Радара: собственные источники, '
                    'неограниченные пространства и инструменты, которые '
                    'превращают ленту в ваше медиа.',
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 14,
                      height: 1.5,
                      color: Colors.white.withValues(alpha: 0.7),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _CycleToggle extends StatelessWidget {
  const _CycleToggle({required this.cycle, required this.onChanged});

  final _Cycle cycle;
  final ValueChanged<_Cycle> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: NFColors.surface2,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        children: <Widget>[
          Expanded(child: _cycleTab(_Cycle.month)),
          const SizedBox(width: 4),
          Expanded(child: _cycleTab(_Cycle.year)),
        ],
      ),
    );
  }

  Widget _cycleTab(_Cycle c) {
    final bool on = cycle == c;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => onChanged(c),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: on ? NFColors.surface : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
          boxShadow: on
              ? <BoxShadow>[
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.06),
                    blurRadius: 2,
                    offset: const Offset(0, 1),
                  ),
                ]
              : const <BoxShadow>[],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(
              c.label,
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: on ? NFColors.ink : NFColors.mute,
              ),
            ),
            if (c == _Cycle.year) ...<Widget>[
              const SizedBox(width: 6),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(
                  color: NFColors.lime,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text(
                  '-30%',
                  style: TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.5,
                    color: NFColors.limeInk,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _PriceCard extends StatelessWidget {
  const _PriceCard({required this.cycle});

  final _Cycle cycle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.ink, width: 2),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: <Widget>[
              Text(
                cycle.price,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 34,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -1.2,
                  color: NFColors.ink,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                cycle.per,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: NFColors.mute,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            cycle.note,
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 13,
              color: NFColors.mute,
            ),
          ),
        ],
      ),
    );
  }
}

class _FeaturesList extends StatelessWidget {
  static const List<_Feature> _features = <_Feature>[
    _Feature(
      icon: 'radar',
      title: 'Собственные источники',
      desc:
          'Добавляйте любые Telegram-каналы, хабы Habr и разделы VC.RU. '
          'До 100 источников вместо 20.',
    ),
    _Feature(
      icon: 'layers',
      title: 'Безлимитные пространства',
      desc:
          'Собирайте любое число тематических подборок с индивидуальным '
          'цветом и набором источников.',
    ),
    _Feature(
      icon: 'check',
      title: 'Нет рекламы',
      desc:
          'Чистая лента. Никаких промо-карточек между материалами '
          'и никакой внешней аналитики.',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(6, 0, 6, 10),
          child: NFText.mono('ЧТО ВКЛЮЧЕНО · ${_features.length}'),
        ),
        for (int i = 0; i < _features.length; i++) ...<Widget>[
          _FeatureTile(feature: _features[i], indexEven: i % 2 == 0),
          if (i != _features.length - 1) const SizedBox(height: 8),
        ],
      ],
    );
  }
}

class _Feature {
  const _Feature({
    required this.icon,
    required this.title,
    required this.desc,
  });
  final String icon;
  final String title;
  final String desc;
}

class _FeatureTile extends StatelessWidget {
  const _FeatureTile({required this.feature, required this.indexEven});

  final _Feature feature;
  final bool indexEven;

  @override
  Widget build(BuildContext context) {
    final Color disk = indexEven ? NFColors.ink : NFColors.lime;
    final Color fg = indexEven ? NFColors.lime : NFColors.limeInk;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: const BorderRadius.all(Radius.circular(NFRadii.radius)),
        border: Border.all(color: NFColors.hairline),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: disk,
              borderRadius: BorderRadius.circular(10),
            ),
            alignment: Alignment.center,
            child: NFIcon(feature.icon, size: 18, color: fg),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  feature.title,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.3,
                    color: NFColors.ink,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  feature.desc,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 13,
                    height: 1.45,
                    color: NFColors.mute,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ComparisonTable extends StatelessWidget {
  static const List<List<String>> _rows = <List<String>>[
    <String>['Пространства', '—', 'без лимита'],
    <String>['Свои источники', '—', '✓'],
    <String>['Без рекламы', '—', '✓'],
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(6, 0, 6, 10),
          child: NFText.mono('FREE · PREMIUM'),
        ),
        ClipRRect(
          borderRadius: NFRadii.brLg,
          child: Container(
            decoration: BoxDecoration(
              color: NFColors.surface,
              borderRadius: NFRadii.brLg,
              border: Border.all(color: NFColors.hairline),
            ),
            child: Column(
              children: <Widget>[
                Container(
                  color: NFColors.surface2,
                  padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
                  child: Row(
                    children: const <Widget>[
                      Expanded(
                        flex: 12,
                        child: NFText.mono('ФУНКЦИЯ'),
                      ),
                      Expanded(
                        flex: 9,
                        child: NFText.mono(
                          'FREE',
                          textAlign: TextAlign.center,
                        ),
                      ),
                      Expanded(
                        flex: 9,
                        child: NFText.mono(
                          'PREMIUM',
                          color: NFColors.accent,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                ),
                for (int i = 0; i < _rows.length; i++)
                  _ComparisonRow(
                    row: _rows[i],
                    isLast: i == _rows.length - 1,
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _ComparisonRow extends StatelessWidget {
  const _ComparisonRow({required this.row, required this.isLast});

  final List<String> row;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final bool premiumDash = row[2] == '—';
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
        border: Border(
          bottom: isLast
              ? BorderSide.none
              : const BorderSide(color: NFColors.hairline),
        ),
      ),
      child: Row(
        children: <Widget>[
          Expanded(
            flex: 12,
            child: Text(
              row[0],
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 13.5,
                fontWeight: FontWeight.w500,
                color: NFColors.ink,
              ),
            ),
          ),
          Expanded(
            flex: 9,
            child: Text(
              row[1],
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 11,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.3,
                color: NFColors.mute,
              ),
            ),
          ),
          Expanded(
            flex: 9,
            child: Text(
              row[2],
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 11,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.3,
                color: premiumDash ? NFColors.mute : NFColors.accent,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CtaCard extends ConsumerStatefulWidget {
  const _CtaCard({required this.status, required this.cycle});

  final SubscriptionStatus status;
  final _Cycle cycle;

  @override
  ConsumerState<_CtaCard> createState() => _CtaCardState();
}

class _CtaCardState extends ConsumerState<_CtaCard> {
  bool _loading = false;

  Future<void> _checkout() async {
    setState(() => _loading = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final result = await ref
          .read(subscriptionNotifierProvider.notifier)
          .checkout(widget.cycle.planId);
      final uri = Uri.parse(result.confirmationUrl);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        messenger.showSnackBar(
          const SnackBar(content: Text('Не удалось открыть страницу оплаты')),
        );
        return;
      }
      if (!mounted) return;
      await _PaymentWaitDialog.show(context, ref);
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Error: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _cancelAutoRenew() async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Отменить автопродление?'),
        content: const Text(
          'Премиум-доступ останется активным до даты окончания, '
          'но не будет продлён автоматически.',
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Оставить Premium'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Отменить продление',
              style: TextStyle(color: NFColors.warn),
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _loading = true);
    try {
      await ref.read(subscriptionNotifierProvider.notifier).cancel();
      messenger.showSnackBar(
        const SnackBar(content: Text('Автопродление отменено')),
      );
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Error: $e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '—';
    const months = <String>[
      'ЯНВАРЯ', 'ФЕВРАЛЯ', 'МАРТА', 'АПРЕЛЯ', 'МАЯ', 'ИЮНЯ',
      'ИЮЛЯ', 'АВГУСТА', 'СЕНТЯБРЯ', 'ОКТЯБРЯ', 'НОЯБРЯ', 'ДЕКАБРЯ',
    ];
    return '${dt.day} ${months[dt.month - 1]} ${dt.year}';
  }

  @override
  Widget build(BuildContext context) {
    final bool isPremium = widget.status.isPremium;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: NFColors.bg,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: NFColors.hairline),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            offset: const Offset(0, -10),
            blurRadius: 30,
          ),
        ],
      ),
      child: isPremium ? _buildPremium() : _buildFree(),
    );
  }

  Widget _buildFree() {
    return Column(
      children: <Widget>[
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: _loading ? null : _checkout,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            decoration: BoxDecoration(
              color: NFColors.lime,
              borderRadius: BorderRadius.circular(999),
              boxShadow: <BoxShadow>[
                BoxShadow(
                  color: NFColors.lime.withValues(alpha: 0.6),
                  offset: const Offset(0, 10),
                  blurRadius: 30,
                ),
              ],
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                if (_loading)
                  const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: NFColors.limeInk,
                    ),
                  )
                else ...<Widget>[
                  Text(
                    'Оформить · ${widget.cycle.price} ${widget.cycle.perCta}',
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.2,
                      color: NFColors.limeInk,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const NFIcon(
                    'arrow-up-right',
                    size: 15,
                    color: NFColors.limeInk,
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 6),
        const NFText.mono(
          '7 ДНЕЙ БЕСПЛАТНО · ОТМЕНА В ЛЮБОЙ МОМЕНТ',
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildPremium() {
    final String expires = _formatDate(widget.status.expiresAt);
    return Column(
      children: <Widget>[
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: _loading || !widget.status.autoRenew ? null : _cancelAutoRenew,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              color: NFColors.surface,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: NFColors.hairline),
            ),
            child: Center(
              child: _loading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(
                      widget.status.autoRenew
                          ? 'Управлять подпиской'
                          : 'Автопродление отключено',
                      style: const TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: NFColors.ink,
                      ),
                    ),
            ),
          ),
        ),
        const SizedBox(height: 6),
        NFText.mono(
          'СЛЕДУЮЩЕЕ СПИСАНИЕ · $expires',
          textAlign: TextAlign.center,
        ),
      ],
    );
  }
}

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
        children: <Widget>[
          const Text(
            'Завершите оплату в открывшейся вкладке. '
            'Статус обновится автоматически, как только мы получим подтверждение.',
          ),
          const SizedBox(height: 12),
          Row(
            children: const <Widget>[
              SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Проверяем статус…',
                  style: TextStyle(fontSize: 13, color: NFColors.mute),
                ),
              ),
            ],
          ),
          if (_errorText != null) ...<Widget>[
            const SizedBox(height: 8),
            Text(
              _errorText!,
              style: const TextStyle(fontSize: 12, color: NFColors.warn),
            ),
          ],
        ],
      ),
      actions: <Widget>[
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

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Text('Не удалось загрузить подписку.'),
          const SizedBox(height: 8),
          TextButton(
            onPressed: onRetry,
            child: const Text('Повторить'),
          ),
        ],
      ),
    );
  }
}
