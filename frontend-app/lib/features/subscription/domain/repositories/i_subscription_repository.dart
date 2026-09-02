import '../models/checkout_result.dart';
import '../models/subscription_status.dart';

abstract interface class ISubscriptionRepository {
  Future<SubscriptionStatus> getStatus();
  Future<CheckoutResult> checkout(String plan);
  Future<void> cancelAutoRenewal();
}
