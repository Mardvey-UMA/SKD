/// DTO for register response: {message, email}.
class RegisterResponseDTO {
  const RegisterResponseDTO({required this.message, required this.email});

  final String message;
  final String email;

  factory RegisterResponseDTO.fromJson(Map<String, dynamic> json) =>
      RegisterResponseDTO(
        message: json['message'] as String,
        email: json['email'] as String,
      );
}

/// DTO for generic message responses (logout, password reset, etc.).
class MessageResponseDTO {
  const MessageResponseDTO({required this.message});

  final String message;

  factory MessageResponseDTO.fromJson(Map<String, dynamic> json) =>
      MessageResponseDTO(message: json['message'] as String);
}
