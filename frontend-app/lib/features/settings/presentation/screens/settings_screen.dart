import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../../../../shared/widgets/profile_list_item.dart';
import '../../../../shared/widgets/section_label.dart';
import '../../../../theme/app_tokens.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../onboarding/presentation/providers/onboarding_provider.dart';
import '../../../profile/presentation/providers/profile_provider.dart';
import '../../../subscription/presentation/providers/subscription_provider.dart';
import '../providers/settings_provider.dart';
import '../../../../shared/widgets/icon_tile.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(userProfileNotifierProvider);
    final settingsAsync = ref.watch(settingsNotifierProvider);
    final isPremium =
        ref.watch(subscriptionNotifierProvider).valueOrNull?.isPremium ?? false;
    final tokens = Theme.of(context).extension<AppTokens>()!;

    return Scaffold(
      backgroundColor: tokens.colors.surface.background,
      body: SafeArea(
        child: settingsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: TextButton(
              onPressed: () => ref.invalidate(settingsNotifierProvider),
              child: const Text('Повторить'),
            ),
          ),
          data: (settings) {
            final email =
                profileAsync.valueOrNull?.email ?? settings.email;

            return SingleChildScrollView(
              padding: EdgeInsets.symmetric(horizontal: tokens.spacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(height: tokens.spacing.md),
                  Text(
                    'Настройки',
                    style: tokens.typography.headingL.copyWith(
                      color: tokens.colors.text.primary,
                    ),
                  ),

                  // ── АККАУНТ ──────────────────────────────────────────────
                  const SectionLabel('Аккаунт'),
                  _GroupCard(
                    children: [
                      ProfileListItem(
                        iconVariant: AccentVariant.sky,
                        icon: Icons.email_outlined,
                        title: 'Эл. почта',
                        trailing: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 140),
                          child: Text(
                            email,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            textAlign: TextAlign.end,
                            style: tokens.typography.bodyS.copyWith(
                              color: tokens.colors.text.tertiary,
                            ),
                          ),
                        ),
                      ),
                      _Divider(),
                      ProfileListItem(
                        iconVariant: AccentVariant.violet,
                        icon: Icons.lock_outline,
                        title: 'Пароль и безопасность',
                        onTap: () => context.push('/change-password'),
                      ),
                      _Divider(),
                      ProfileListItem(
                        iconVariant: AccentVariant.amber,
                        icon: Icons.workspace_premium_outlined,
                        title: 'Управление подпиской',
                        onTap: () => context.push('/subscription'),
                      ),
                    ],
                  ),

                  // ── НАСТРОЙКИ КОНТЕНТА ────────────────────────────────────
                  const SectionLabel('Настройки контента'),
                  _GroupCard(
                    children: [
                      ProfileListItem(
                        iconVariant: AccentVariant.amber,
                        icon: Icons.folder_outlined,
                        title: 'Каталог источников',
                        onTap: () => context.push('/sources'),
                      ),
                      _Divider(),
                      ProfileListItem(
                        iconVariant: AccentVariant.rose,
                        icon: Icons.visibility_off_outlined,
                        title: 'Скрытые источники',
                        onTap: () => context.push('/blocked-sources'),
                      ),
                      if (isPremium) ...[
                        _Divider(),
                        ProfileListItem(
                          iconVariant: AccentVariant.sky,
                          icon: Icons.grid_view_rounded,
                          title: 'Мои пространства',
                          onTap: () => context.push('/spaces'),
                        ),
                        _Divider(),
                        ProfileListItem(
                          iconVariant: AccentVariant.mint,
                          icon: Icons.add_box_outlined,
                          title: 'Мои добавленные',
                          onTap: () => context.push('/my-additions'),
                        ),
                      ],
                    ],
                  ),

                  // ── ВЫЙТИ ────────────────────────────────────────────────
                  SizedBox(height: tokens.spacing.xl),
                  _GroupCard(
                    children: [
                      ProfileListItem(
                        iconVariant: AccentVariant.rose,
                        icon: Icons.logout,
                        title: 'Выйти',
                        destructive: true,
                        onTap: () async {
                          final onboardingRepo = ref.read(
                            onboardingRepositoryProvider,
                          );
                          await onboardingRepo.clearOnboarding();
                          ref.invalidate(onboardingStatusProvider);
                          ref.read(authNotifierProvider.notifier).logout();
                        },
                      ),
                    ],
                  ),

                  // ── Версия ────────────────────────────────────────────────
                  SizedBox(height: tokens.spacing.xl),
                  const _AppVersionRow(),
                  SizedBox(height: tokens.spacing.xl2),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _GroupCard extends StatelessWidget {
  const _GroupCard({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    return Container(
      decoration: BoxDecoration(
        color: tokens.colors.surface.base,
        borderRadius: AppRadii.blg,
        boxShadow: AppElevation.elev1,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }
}

class _Divider extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    return Divider(
      height: 1,
      color: tokens.colors.border.soft,
      indent: 72,
    );
  }
}

class _AppVersionRow extends StatefulWidget {
  const _AppVersionRow();

  @override
  State<_AppVersionRow> createState() => _AppVersionRowState();
}

class _AppVersionRowState extends State<_AppVersionRow> {
  String _version = '';

  @override
  void initState() {
    super.initState();
    PackageInfo.fromPlatform().then((info) {
      if (mounted) {
        setState(() => _version = 'v${info.version}+${info.buildNumber}');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_version.isEmpty) return const SizedBox.shrink();
    final tokens = Theme.of(context).extension<AppTokens>()!;
    return Center(
      child: Text(
        'Версия приложения: $_version',
        style: tokens.typography.caption.copyWith(
          color: tokens.colors.text.disabled,
        ),
        textAlign: TextAlign.center,
      ),
    );
  }
}
