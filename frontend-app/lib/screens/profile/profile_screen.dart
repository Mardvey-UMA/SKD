import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/profile/domain/models/user_profile.dart';
import '../../features/profile/presentation/providers/profile_provider.dart';
import '../../theme/colors.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';

/// Profile — redesigned landing screen.
///
/// Mirrors `ProfileScreen` in `design/reference/mockup/screens.jsx`:
/// lime avatar tile (first letter of email), email headline, mono
/// email-subtitle, optional PREMIUM chip, and six list rows — bookmarks,
/// likes, dislikes, sources, plan, settings. No stats dashboard, no
/// invented sections.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<UserProfile> profileAsync = ref.watch(
      userProfileNotifierProvider,
    );

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: profileAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (Object _, _) => _ErrorView(
            onRetry: () => ref.invalidate(userProfileNotifierProvider),
          ),
          data: (UserProfile profile) => _ProfileView(profile: profile),
        ),
      ),
    );
  }
}

class _ProfileView extends StatelessWidget {
  const _ProfileView({required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    final String email = profile.email;
    final String avatarLetter = email.isNotEmpty
        ? email.substring(0, 1).toUpperCase()
        : '?';
    final bool isPremium = profile.subscriptionTier == 'premium';

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 130),
      children: <Widget>[
        _Header(
          avatarLetter: avatarLetter,
          email: email,
          isPremium: isPremium,
        ),
        const SizedBox(height: 18),
        _ProfileRow(
          icon: 'bookmark',
          title: 'Сохранённое',
          onTap: () => context.go('/shell/bookmarks?kind=bookmark'),
        ),
        const _RowDivider(),
        _ProfileRow(
          icon: 'thumb-up',
          title: 'Понравилось',
          onTap: () => context.go('/shell/bookmarks?kind=like'),
        ),
        const _RowDivider(),
        _ProfileRow(
          icon: 'thumb-down',
          title: 'Не понравилось',
          onTap: () => context.go('/shell/bookmarks?kind=dislike'),
        ),
        const _RowDivider(),
        _ProfileRow(
          icon: 'radar',
          title: 'Источники',
          onTap: () => context.push('/sources'),
        ),
        const _RowDivider(),
        _ProfileRow(
          icon: 'star',
          title: 'План',
          onTap: () => context.push('/subscription'),
        ),
        const _RowDivider(),
        _ProfileRow(
          icon: 'gear',
          title: 'Настройки',
          onTap: () => context.go('/shell/settings'),
        ),
      ],
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({
    required this.avatarLetter,
    required this.email,
    required this.isPremium,
  });

  final String avatarLetter;
  final String email;
  final bool isPremium;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        Container(
          width: 72,
          height: 72,
          decoration: const BoxDecoration(
            color: NFColors.lime,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Text(
            avatarLetter,
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 32,
              fontWeight: FontWeight.w800,
              color: NFColors.limeInk,
              height: 1.0,
            ),
          ),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text(
                email,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                  letterSpacing: -0.6,
                  color: NFColors.ink,
                  height: 1.1,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 2),
              NFText.mono('ПОЧТА · $email'),
              if (isPremium) ...<Widget>[
                const SizedBox(height: 8),
                const _PremiumBadge(),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _PremiumBadge extends StatelessWidget {
  const _PremiumBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: NFColors.lime,
        borderRadius: BorderRadius.circular(999),
      ),
      child: const Text(
        'PREMIUM',
        style: TextStyle(
          fontFamily: 'Nunito',
          fontSize: 11,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.6,
          color: NFColors.limeInk,
          height: 1.0,
        ),
      ),
    );
  }
}

class _ProfileRow extends StatelessWidget {
  const _ProfileRow({
    required this.icon,
    required this.title,
    required this.onTap,
  });

  final String icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: title,
      button: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 14),
          child: Row(
            children: <Widget>[
              SizedBox(
                width: 24,
                child: NFIcon(icon, size: 20, color: NFColors.ink),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.4,
                    color: NFColors.ink,
                    height: 1.2,
                  ),
                ),
              ),
              const NFIcon('chevron', size: 16, color: NFColors.mute),
            ],
          ),
        ),
      ),
    );
  }
}

class _RowDivider extends StatelessWidget {
  const _RowDivider();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(left: 38),
      child: Divider(
        height: 1,
        thickness: 1,
        color: NFColors.hairline,
      ),
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
          const Text('Не удалось загрузить профиль.'),
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
