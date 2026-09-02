import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../features/auth/presentation/providers/auth_provider.dart';
import '../../features/onboarding/presentation/providers/onboarding_provider.dart';
import '../../features/profile/presentation/providers/profile_provider.dart';
import '../../features/subscription/presentation/providers/subscription_provider.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';

/// Settings — redesigned landing screen.
///
/// Mirrors `SettingsScreen` in `design/reference/mockup/screens.jsx`: a
/// large «Настройки» title followed by four grouped sections (АККАУНТ /
/// ПОДПИСКА / ИСТОЧНИКИ / ПРИЛОЖЕНИЕ). Each row is a uniform tappable
/// line with title, optional mono trailing detail, and a chevron.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(userProfileNotifierProvider);
    final bool isPremium =
        ref.watch(subscriptionNotifierProvider).valueOrNull?.isPremium ?? false;
    final String email = profileAsync.valueOrNull?.email ?? '';

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 130),
          children: <Widget>[
            const Padding(
              padding: EdgeInsets.fromLTRB(4, 0, 4, 18),
              child: Text(
                'Настройки',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 40,
                  height: 0.95,
                  letterSpacing: -1.4,
                  fontWeight: FontWeight.w700,
                  color: NFColors.ink,
                ),
              ),
            ),
            _Section(
              header: 'АККАУНТ',
              rows: <Widget>[
                _SettingRow(
                  title: 'Email',
                  detailText: email.isEmpty ? null : email,
                ),
                _SettingRow(
                  title: 'Пароль',
                  detailText: 'Сменить',
                  onTap: () => context.push('/change-password'),
                ),
                _SettingRow(
                  title: 'Выйти',
                  destructive: true,
                  onTap: () async {
                    final onboardingRepo =
                        ref.read(onboardingRepositoryProvider);
                    await onboardingRepo.clearOnboarding();
                    ref.invalidate(onboardingStatusProvider);
                    ref.read(authNotifierProvider.notifier).logout();
                  },
                ),
              ],
            ),
            const SizedBox(height: 18),
            _Section(
              header: 'ПОДПИСКА',
              rows: <Widget>[
                _SettingRow(
                  title: isPremium ? 'Управлять Premium' : 'Оформить Premium',
                  detailText: isPremium ? 'Активно' : 'Free',
                  onTap: () => context.push('/subscription'),
                ),
                _SettingRow(
                  title: 'Тарифы и цены',
                  detailText: 'Сравнить',
                  onTap: () => context.push('/subscription'),
                ),
              ],
            ),
            const SizedBox(height: 18),
            _Section(
              header: 'ИСТОЧНИКИ',
              rows: <Widget>[
                _SettingRow(
                  title: 'Источники',
                  detailText: 'Каталог',
                  onTap: () => context.push('/sources'),
                ),
                _SettingRow(
                  title: 'Мои добавленные',
                  detailText: 'Список',
                  onTap: () => context.push('/my-additions'),
                ),
              ],
            ),
            const SizedBox(height: 18),
            const _Section(
              header: 'ПРИЛОЖЕНИЕ',
              rows: <Widget>[
                _SettingRow(
                  title: 'Версия',
                  detailWidget: _VersionMono(),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.header, required this.rows});

  final String header;
  final List<Widget> rows;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(6, 0, 6, 8),
          child: NFText.mono(header),
        ),
        Container(
          decoration: BoxDecoration(
            color: NFColors.surface,
            borderRadius: NFRadii.brLg,
            border: Border.all(color: NFColors.hairline),
          ),
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: <Widget>[
              for (int i = 0; i < rows.length; i++) ...<Widget>[
                rows[i],
                if (i != rows.length - 1)
                  const Divider(
                    height: 1,
                    thickness: 1,
                    color: NFColors.hairline,
                  ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _SettingRow extends StatelessWidget {
  const _SettingRow({
    required this.title,
    this.detailText,
    this.detailWidget,
    this.onTap,
    this.destructive = false,
  });

  final String title;
  final String? detailText;
  final Widget? detailWidget;
  final VoidCallback? onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final Color titleColor = destructive ? NFColors.warn : NFColors.ink;
    final Widget row = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 15,
                fontWeight: FontWeight.w500,
                color: titleColor,
                height: 1.25,
              ),
            ),
          ),
          if (detailWidget != null) ...<Widget>[
            detailWidget!,
            const SizedBox(width: 8),
          ] else if (detailText != null) ...<Widget>[
            NFText.mono(detailText!, color: NFColors.ink2),
            const SizedBox(width: 8),
          ],
          const NFIcon('chevron', size: 13, color: NFColors.mute),
        ],
      ),
    );

    if (onTap == null) return row;
    return Semantics(
      label: title,
      button: true,
      child: InkWell(onTap: onTap, child: row),
    );
  }
}

class _VersionMono extends StatefulWidget {
  const _VersionMono();

  @override
  State<_VersionMono> createState() => _VersionMonoState();
}

class _VersionMonoState extends State<_VersionMono> {
  String _version = '—';

  @override
  void initState() {
    super.initState();
    PackageInfo.fromPlatform().then((info) {
      if (mounted) setState(() => _version = info.version);
    });
  }

  @override
  Widget build(BuildContext context) {
    return NFText.mono(_version, color: NFColors.ink2);
  }
}
