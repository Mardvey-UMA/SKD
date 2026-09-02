import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';
import '../../../../features/auth/presentation/providers/dio_provider.dart';
import '../../data/repositories/api_subscription_repository.dart';
import '../../domain/models/checkout_result.dart';
import '../../domain/models/subscription_status.dart';
import '../../domain/repositories/i_subscription_repository.dart';

final subscriptionRepositoryProvider = Provider<ISubscriptionRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiSubscriptionRepository(dio);
});

class SubscriptionNotifier
    extends StateNotifier<AsyncValue<SubscriptionStatus>> {
  SubscriptionNotifier(this._ref, this._repo) : super(const AsyncLoading()) {
    _load();
  }

  final Ref _ref;
  final ISubscriptionRepository _repo;
  bool _polling = false;

  Future<void> _load() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => _repo.getStatus());
  }

  Future<void> refresh() => _load();

  /// Creates checkout and returns confirmation URL.
  Future<CheckoutResult> checkout(String plan) async {
    return _repo.checkout(plan);
  }

  /// Polls subscription status after checkout until the user's tier becomes
  /// `premium` (YooKassa webhook → auth-service consumer → DB update).
  ///
  /// When tier transitions to premium:
  ///   1. refreshes JWT so api-gateway sees the new `subscription_tier` claim
  ///   2. updates local state
  ///
  /// Returns `true` if premium was reached within the timeout window.
  Future<bool> pollUntilPremium({
    Duration interval = const Duration(seconds: 3),
    Duration timeout = const Duration(minutes: 2),
  }) async {
    if (_polling) return false;
    _polling = true;
    final deadline = DateTime.now().add(timeout);
    try {
      while (DateTime.now().isBefore(deadline)) {
        try {
          final status = await _repo.getStatus();
          state = AsyncData(status);
          if (status.isPremium) {
            await _ref.read(authNotifierProvider.notifier).forceRefreshToken();
            state = AsyncData(await _repo.getStatus());
            return true;
          }
        } catch (_) {
          // Swallow transient errors and keep polling.
        }
        await Future.delayed(interval);
      }
      return false;
    } finally {
      _polling = false;
    }
  }

  /// One-shot: reload status and refresh JWT if tier is now premium.
  /// Use when the user taps "I paid" after returning from YooKassa.
  Future<bool> verifyAfterReturn() async {
    try {
      final status = await _repo.getStatus();
      state = AsyncData(status);
      if (status.isPremium) {
        await _ref.read(authNotifierProvider.notifier).forceRefreshToken();
        state = AsyncData(await _repo.getStatus());
        return true;
      }
    } catch (e, st) {
      state = AsyncError(e, st);
    }
    return false;
  }

  Future<void> cancel() async {
    await _repo.cancelAutoRenewal();
    await _load();
  }
}

final subscriptionNotifierProvider =
    StateNotifierProvider<SubscriptionNotifier, AsyncValue<SubscriptionStatus>>(
      (ref) {
        final repo = ref.watch(subscriptionRepositoryProvider);
        return SubscriptionNotifier(ref, repo);
      },
    );
