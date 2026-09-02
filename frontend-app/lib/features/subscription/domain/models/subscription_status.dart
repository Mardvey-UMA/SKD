import 'package:equatable/equatable.dart';

class SubscriptionStatus extends Equatable {
  const SubscriptionStatus({
    required this.tier,
    required this.status,
    this.planId,
    this.expiresAt,
    required this.autoRenew,
  });

  /// "free" | "premium"
  final String tier;

  /// "active" | "expired" | "pending"
  final String status;

  /// "premium_monthly" | "premium_yearly" | null
  final String? planId;

  final DateTime? expiresAt;
  final bool autoRenew;

  bool get isPremium => tier == 'premium';
  bool get isActive => status == 'active';

  factory SubscriptionStatus.fromJson(Map<String, dynamic> json) =>
      SubscriptionStatus(
        tier: json['tier'] as String,
        status: json['status'] as String,
        planId: (json['plan_id'] ?? json['planId']) as String?,
        expiresAt: (json['expires_at'] ?? json['expiresAt']) != null
            ? DateTime.parse(
                (json['expires_at'] ?? json['expiresAt']) as String,
              )
            : null,
        autoRenew: (json['auto_renew'] ?? json['autoRenew']) as bool? ?? false,
      );

  @override
  List<Object?> get props => [tier, status, planId, expiresAt, autoRenew];
}
