import '../../domain/entities/user.dart';

/// Data Transfer Object for User with JSON serialization.
class UserDTO {
  const UserDTO({required this.id, required this.email, required this.name});

  final String id;
  final String email;
  final String name;

  factory UserDTO.fromJson(Map<String, dynamic> json) => UserDTO(
    id: json['id'] as String,
    email: json['email'] as String,
    name: json['name'] as String,
  );

  Map<String, dynamic> toJson() => {'id': id, 'email': email, 'name': name};

  /// Converts DTO to domain entity.
  User toEntity() => User(id: id, email: email, name: name);
}
