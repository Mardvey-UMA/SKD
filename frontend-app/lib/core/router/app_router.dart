import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/auth/domain/entities/auth_state.dart';
import '../../features/auth/presentation/providers/auth_provider.dart';
import '../../features/auth/presentation/screens/change_password_screen.dart';
import '../../features/auth/presentation/screens/email_verification_code_screen.dart';
import '../../features/auth/presentation/screens/forgot_password_screen.dart';
import '../../features/auth/presentation/screens/registration_screen.dart';
import '../../features/auth/presentation/screens/reset_password_screen.dart';
import '../../features/blocked_sources/presentation/screens/blocked_sources_screen.dart';
import '../../features/spaces/presentation/screens/space_detail_screen.dart';
import '../../features/spaces/presentation/screens/spaces_screen.dart';
import '../../features/subscription/domain/models/subscription_status.dart';
import '../../features/subscription/presentation/providers/subscription_provider.dart';
import '../../features/feed/presentation/screens/related_list_screen.dart';
import '../../screens/bookmarks/bookmarks_screen.dart';
import '../../screens/collections/collection_editor.dart';
import '../../screens/collections/collections_screen.dart'
    as collections_redesign;
import '../../screens/detail/detail_screen.dart';
import '../../screens/feed/feed_screen.dart';
import '../../screens/settings/add_source_screen.dart';
import '../../screens/settings/my_sources_screen.dart';
import '../../screens/settings/plan_screen.dart';
import '../../screens/settings/settings_screen.dart';
import '../../screens/settings/sources_screen.dart';
import '../../features/onboarding/presentation/providers/onboarding_provider.dart';
import '../../screens/onboarding/topics_screen.dart';
import '../../screens/onboarding/welcome_screen.dart';
import '../../screens/profile/profile_screen.dart';
import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../ui/motion/page_transitions.dart';
import '../../ui/shell/responsive_shell.dart';

final goRouterProvider = Provider<GoRouter>((ref) {
  final routerListenable = _RouterListenable();

  ref.listen<AsyncValue<AuthState>>(
    authNotifierProvider,
    (_, _) => routerListenable.notify(),
  );

  ref.listen<AsyncValue<bool>>(
    onboardingStatusProvider,
    (_, _) => routerListenable.notify(),
  );

  // Only trigger router refresh when subscription tier actually changes, not
  // on every state transition — otherwise premium-gated navigations can race
  // with the status poll and bounce the user back unexpectedly.
  ref.listen<AsyncValue<SubscriptionStatus>>(
    subscriptionNotifierProvider,
    (prev, next) {
      final prevTier = prev?.valueOrNull?.tier;
      final nextTier = next.valueOrNull?.tier;
      if (prevTier != nextTier) routerListenable.notify();
    },
  );

  return GoRouter(
    initialLocation: '/login',
    debugLogDiagnostics: true,
    refreshListenable: routerListenable,
    redirect: (context, state) {
      final authAsync = ref.read(authNotifierProvider);
      if (authAsync.isLoading) return null;

      final authValue = authAsync.valueOrNull;
      final isAuthenticated = authValue?.isAuthenticated ?? false;
      final isPendingCodeVerification =
          authValue?.pendingCodeVerification ?? false;

      final onboardingAsync = ref.read(onboardingStatusProvider);
      if (onboardingAsync.isLoading) return null;
      final hasCompletedOnboarding = onboardingAsync.valueOrNull ?? false;

      final loc = state.matchedLocation;
      final goingToAuth = loc == '/login' ||
          loc == '/register' ||
          loc == '/auth/login' ||
          loc == '/auth/register';
      final goingToVerifyCode = loc == '/verify-code';
      final goingToPasswordReset =
          loc == '/forgot-password' || loc == '/reset-password';
      final goingToOnboarding = loc.startsWith('/onboarding');
      final goingToShell = loc.startsWith('/shell');

      // Always allow password reset screens (no auth needed)
      if (goingToPasswordReset) return null;

      // After register — redirect to 6-digit code verification
      if (isPendingCodeVerification && !goingToVerifyCode) {
        final email = Uri.encodeQueryComponent(authValue?.pendingEmail ?? '');
        return '/verify-code?email=$email';
      }

      // Not authenticated → force login
      if (!isAuthenticated && !goingToAuth && !goingToVerifyCode) {
        return '/login';
      }

      // Authenticated → redirect away from auth screens
      if (isAuthenticated && goingToAuth) {
        return hasCompletedOnboarding ? '/shell/feed' : '/onboarding';
      }

      // Authenticated but not onboarded → onboarding
      if (isAuthenticated && !hasCompletedOnboarding && goingToShell) {
        return '/onboarding';
      }

      // Authenticated + onboarded → skip onboarding
      if (isAuthenticated && hasCompletedOnboarding && goingToOnboarding) {
        return '/shell/feed';
      }

      // Premium gate for Phase 4 screens (Sources Phase 4)
      if (isAuthenticated) {
        const premiumPrefixes = <String>[
          '/sources/add',
          '/spaces',
          '/my-additions',
        ];
        final requiresPremium = premiumPrefixes.any(
          (p) => loc == p || loc.startsWith('$p/'),
        );
        if (requiresPremium && loc != '/subscription') {
          final tier = ref
              .read(subscriptionNotifierProvider)
              .valueOrNull
              ?.tier;
          if (tier != 'premium') {
            return '/subscription';
          }
        }
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => const WelcomeScreen(),
      ),
      GoRoute(
        path: '/auth/login',
        name: 'authLogin',
        builder: (context, state) => const WelcomeScreen(),
      ),
      GoRoute(
        path: '/register',
        name: 'register',
        builder: (context, state) => const RegistrationScreen(),
      ),
      GoRoute(
        path: '/auth/register',
        name: 'authRegister',
        builder: (context, state) => const RegistrationScreen(),
      ),
      GoRoute(
        path: '/verify-code',
        name: 'verifyCode',
        builder: (context, state) {
          final email = state.uri.queryParameters['email'] ?? '';
          return EmailVerificationCodeScreen(email: email);
        },
      ),
      GoRoute(
        path: '/forgot-password',
        name: 'forgotPassword',
        builder: (context, state) => const ForgotPasswordScreen(),
      ),
      GoRoute(
        path: '/reset-password',
        name: 'resetPassword',
        builder: (context, state) {
          final token = state.uri.queryParameters['token'];
          return ResetPasswordScreen(token: token);
        },
      ),
      GoRoute(
        path: '/onboarding',
        name: 'onboarding',
        pageBuilder: (context, state) =>
            buildFadeRisePage(child: const TopicsScreen()),
      ),
      GoRoute(
        path: '/onboarding/topics',
        name: 'onboardingTopics',
        pageBuilder: (context, state) =>
            buildFadeRisePage(child: const TopicsScreen()),
      ),
      GoRoute(
        path: '/subscription',
        name: 'subscription',
        builder: (context, state) => const PlanScreen(),
      ),
      GoRoute(
        path: '/change-password',
        name: 'changePassword',
        builder: (context, state) => const ChangePasswordScreen(),
      ),
      GoRoute(
        path: '/sources',
        name: 'sourcesCatalog',
        builder: (context, state) => SourcesScreen(
          highlightId: state.uri.queryParameters['highlight'],
        ),
        routes: [
          GoRoute(
            path: 'add',
            name: 'addSource',
            builder: (context, state) => const AddSourceScreen(),
          ),
        ],
      ),
      GoRoute(
        path: '/spaces',
        name: 'spaces',
        builder: (context, state) => const SpacesScreen(),
        routes: [
          GoRoute(
            path: 'new',
            name: 'spaceNew',
            builder: (context, state) => const CollectionEditorScreen(),
          ),
          GoRoute(
            path: ':id',
            name: 'spaceDetail',
            builder: (context, state) => SpaceDetailScreen(
              spaceId: state.pathParameters['id']!,
            ),
            routes: [
              GoRoute(
                path: 'edit',
                name: 'spaceEdit',
                builder: (context, state) => CollectionEditorScreen(
                  existingSpaceId: state.pathParameters['id'],
                ),
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        path: '/blocked-sources',
        name: 'blockedSources',
        builder: (context, state) => const BlockedSourcesScreen(),
      ),
      GoRoute(
        path: '/my-additions',
        name: 'myAdditions',
        builder: (context, state) => const MySourcesScreen(),
      ),
      GoRoute(
        path: '/article/:articleId',
        name: 'articleStandalone',
        pageBuilder: (context, state) => buildFadeRisePage(
          child: DetailScreen(
            articleId: state.pathParameters['articleId']!,
          ),
        ),
      ),
      ShellRoute(
        builder: (context, state, child) {
          final Breakpoint bp = context.breakpoint;
          final int activeTab = _indexFrom(state.uri);
          return ResponsiveShell(
            bp: bp,
            activeTab: activeTab,
            onTab: (int i) => _goTab(context, i),
            child: child,
          );
        },
        routes: [
          GoRoute(
            path: '/shell/feed',
            name: 'feed',
            pageBuilder: (context, state) =>
                buildFadeRisePage(child: const FeedScreen()),
            routes: [
              GoRoute(
                path: ':articleId',
                name: 'articleDetail',
                pageBuilder: (context, state) => buildFadeRisePage(
                  child: DetailScreen(
                    articleId: state.pathParameters['articleId']!,
                  ),
                ),
                routes: [
                  GoRoute(
                    path: 'related',
                    name: 'relatedList',
                    pageBuilder: (context, state) => buildFadeRisePage(
                      child: RelatedListScreen(
                        parentArticleId: state.pathParameters['articleId']!,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          GoRoute(
            path: '/shell/collections',
            name: 'collections',
            pageBuilder: (context, state) => buildFadeRisePage(
              child: const collections_redesign.CollectionsScreen(),
            ),
          ),
          GoRoute(
            path: '/shell/bookmarks',
            name: 'bookmarks',
            pageBuilder: (context, state) {
              final String kind =
                  state.uri.queryParameters['kind'] ?? 'bookmark';
              final BookmarksKind resolved = switch (kind) {
                'like' => BookmarksKind.like,
                'dislike' => BookmarksKind.dislike,
                _ => BookmarksKind.bookmark,
              };
              return buildFadeRisePage(
                child: BookmarksScreen(kind: resolved),
              );
            },
          ),
          GoRoute(
            path: '/shell/profile',
            name: 'profile',
            pageBuilder: (context, state) =>
                buildFadeRisePage(child: const ProfileScreen()),
          ),
          GoRoute(
            path: '/shell/settings',
            name: 'settings',
            pageBuilder: (context, state) =>
                buildFadeRisePage(child: const SettingsScreen()),
          ),
        ],
      ),
    ],
    errorBuilder: (context, state) =>
        Scaffold(body: Center(child: Text('Страница не найдена: ${state.error}'))),
  );
});

class _RouterListenable extends ChangeNotifier {
  void notify() => notifyListeners();
}

/// Maps the current URL to the tab index consumed by
/// `ResponsiveShell` / `BottomNav` / `SideNav` (`feed=0, collections=1,
/// profile=2, settings=3`).
int _indexFrom(Uri uri) {
  final String loc = uri.path;
  if (loc.startsWith('/shell/collections') ||
      loc.startsWith('/shell/bookmarks') ||
      loc.startsWith('/spaces')) {
    return 1;
  }
  if (loc.startsWith('/shell/profile')) return 2;
  if (loc.startsWith('/shell/settings')) return 3;
  return 0; // default = feed
}

void _goTab(BuildContext context, int index) {
  switch (index) {
    case 0:
      context.go('/shell/feed');
    case 1:
      context.go('/shell/collections');
    case 2:
      context.go('/shell/profile');
    case 3:
      context.go('/shell/settings');
  }
}
