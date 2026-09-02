import '../../domain/models/user_profile.dart';

class UserProfileDto {
  const UserProfileDto({
    required this.id,
    required this.email,
    this.displayName,
    this.avatarUrl,
    required this.subscriptionTier,
    required this.onboardingCompleted,
    required this.createdAt,
  });

  final String id;
  final String email;
  final String? displayName;
  final String? avatarUrl;
  final String subscriptionTier;
  final bool onboardingCompleted;
  final DateTime createdAt;

  factory UserProfileDto.fromJson(Map<String, dynamic> json) => UserProfileDto(
    id: json['id'] as String,
    email: json['email'] as String,
    displayName: json['display_name'] as String?,
    avatarUrl: json['avatar_url'] as String?,
    subscriptionTier: json['subscription_tier'] as String,
    onboardingCompleted: json['onboarding_completed'] as bool,
    createdAt: DateTime.parse(json['created_at'] as String),
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'email': email,
    'display_name': displayName,
    'avatar_url': avatarUrl,
    'subscription_tier': subscriptionTier,
    'onboarding_completed': onboardingCompleted,
    'created_at': createdAt.toIso8601String(),
  };

  UserProfile toDomain() => UserProfile(
    id: id,
    email: email,
    displayName: displayName,
    avatarUrl: avatarUrl,
    subscriptionTier: subscriptionTier,
    onboardingCompleted: onboardingCompleted,
    createdAt: createdAt,
  );
}
