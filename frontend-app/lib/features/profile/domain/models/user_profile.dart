import 'package:equatable/equatable.dart';

class UserProfile extends Equatable {
  const UserProfile({
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

  @override
  List<Object?> get props => [
    id,
    email,
    displayName,
    avatarUrl,
    subscriptionTier,
    onboardingCompleted,
    createdAt,
  ];
}
