import '../models/user_profile.dart';

abstract interface class IProfileRepository {
  Future<UserProfile> getUserProfile();
  Future<UserProfile> updateProfile({String? displayName, String? avatarUrl});
}
