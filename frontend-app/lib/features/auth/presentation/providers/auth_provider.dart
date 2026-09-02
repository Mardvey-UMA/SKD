import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../../core/services/token_cache_provider.dart';
import '../../domain/entities/auth_state.dart';
import '../../../onboarding/presentation/providers/onboarding_provider.dart';
import '../../../profile/presentation/providers/profile_provider.dart';
import 'auth_repository_provider.dart';

part 'auth_provider.g.dart';

/// Provider for authentication state management.
@Riverpod(keepAlive: true)
class AuthNotifier extends _$AuthNotifier {
  @override
  Future<AuthState> build() async {
    // Wait for the token cache to finish loading from persistent storage.
    // Without this await, accessToken is still null on page reload (F5)
    // because initialize() hasn't completed yet.
    await ref.watch(tokenCacheInitProvider.future);
    final tokenCache = ref.watch(tokenCacheServiceProvider);
    final accessToken = tokenCache.accessToken;
    if (accessToken != null) {
      return const AuthState.authenticated();
    }
    return const AuthState.initial();
  }

  /// Logs in with email and password.
  Future<void> login(String email, String password) async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(authRepositoryProvider);
      await repository.login(email, password);
      // Refresh onboarding status now that we have auth tokens
      ref.invalidate(onboardingStatusProvider);
      return const AuthState.authenticated();
    });
  }

  /// Registers a new user.
  /// Navigates to code verification screen after successful registration.
  Future<void> register(String email, String password) async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      final repository = ref.read(authRepositoryProvider);
      await repository.register(email, password);
      return AuthState.pendingCodeVerification(email);
    });
  }

  /// Called after email verification by code succeeds.
  /// Transitions auth state out of pendingCodeVerification so the router
  /// stops forcing redirects back to /verify-code and the user can proceed
  /// to the login screen.
  void onVerificationComplete() {
    state = const AsyncValue.data(AuthState.unauthenticated());
  }

  /// Logs out the current user.
  Future<void> logout() async {
    state = const AsyncValue.loading();
    final repository = ref.read(authRepositoryProvider);
    try {
      await repository.logout();
    } catch (_) {
      // Even if server call fails, clear local tokens
    }
    state = const AsyncValue.data(AuthState.unauthenticated());
  }

  /// Checks stored tokens to restore auth state.
  Future<void> checkAuthStatus() async {
    final tokenCache = ref.read(tokenCacheServiceProvider);
    final accessToken = tokenCache.accessToken;
    if (accessToken != null) {
      state = const AsyncValue.data(AuthState.authenticated());
    } else {
      state = const AsyncValue.data(AuthState.unauthenticated());
    }
  }

  /// Returns true if user is authenticated.
  bool get isAuthenticated {
    return state.valueOrNull?.isAuthenticated ?? false;
  }

  /// Forces a token refresh using the stored refresh token.
  /// Used after subscription changes so JWT `subscription_tier` claim is
  /// re-issued by auth-service (api-gateway reads tier from the JWT claim,
  /// so without refresh the tier appears unchanged until next login).
  Future<bool> forceRefreshToken() async {
    final tokenCache = ref.read(tokenCacheServiceProvider);
    final refresh = tokenCache.refreshToken;
    if (refresh == null) return false;
    try {
      final repository = ref.read(authRepositoryProvider);
      await repository.refresh(refresh);
      ref.invalidate(userProfileNotifierProvider);
      return true;
    } catch (_) {
      return false;
    }
  }
}
