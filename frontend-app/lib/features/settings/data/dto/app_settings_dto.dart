import '../../domain/models/app_settings.dart';

class AppSettingsDto {
  const AppSettingsDto({
    required this.email,
    required this.pushNotificationsEnabled,
    required this.digestEmailsEnabled,
    required this.language,
    required this.hiddenCategoriesCount,
  });

  final String email;
  final bool pushNotificationsEnabled;
  final bool digestEmailsEnabled;
  final String language;
  final int hiddenCategoriesCount;

  factory AppSettingsDto.fromJson(Map<String, dynamic> json) => AppSettingsDto(
    email: json['email'] as String,
    pushNotificationsEnabled: json['pushNotificationsEnabled'] as bool,
    digestEmailsEnabled: json['digestEmailsEnabled'] as bool,
    language: json['language'] as String,
    hiddenCategoriesCount: json['hiddenCategoriesCount'] as int,
  );

  Map<String, dynamic> toJson() => {
    'email': email,
    'pushNotificationsEnabled': pushNotificationsEnabled,
    'digestEmailsEnabled': digestEmailsEnabled,
    'language': language,
    'hiddenCategoriesCount': hiddenCategoriesCount,
  };

  AppSettings toDomain() => AppSettings(
    email: email,
    pushNotificationsEnabled: pushNotificationsEnabled,
    digestEmailsEnabled: digestEmailsEnabled,
    language: language,
    hiddenCategoriesCount: hiddenCategoriesCount,
  );
}
