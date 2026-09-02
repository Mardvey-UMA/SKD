import 'package:equatable/equatable.dart';

class AppSettings extends Equatable {
  const AppSettings({
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

  AppSettings copyWith({
    String? email,
    bool? pushNotificationsEnabled,
    bool? digestEmailsEnabled,
    String? language,
    int? hiddenCategoriesCount,
  }) => AppSettings(
    email: email ?? this.email,
    pushNotificationsEnabled:
        pushNotificationsEnabled ?? this.pushNotificationsEnabled,
    digestEmailsEnabled: digestEmailsEnabled ?? this.digestEmailsEnabled,
    language: language ?? this.language,
    hiddenCategoriesCount: hiddenCategoriesCount ?? this.hiddenCategoriesCount,
  );

  @override
  List<Object?> get props => [
    email,
    pushNotificationsEnabled,
    digestEmailsEnabled,
    language,
    hiddenCategoriesCount,
  ];
}
