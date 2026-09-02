/// Represents an error response from the backend API.
/// Format: {error, message, request_id?, timestamp?, details?}
class ApiError {
  const ApiError({
    required this.error,
    required this.message,
    this.requestId,
    this.timestamp,
    this.details,
  });

  final String error;
  final String message;
  final String? requestId;
  final String? timestamp;
  final Map<String, dynamic>? details;

  factory ApiError.fromJson(Map<String, dynamic> json) => ApiError(
    error: (json['error'] as String?) ?? 'unknown_error',
    message: (json['message'] as String?) ?? 'An error occurred.',
    requestId: json['request_id'] as String?,
    timestamp: json['timestamp'] as String?,
    details: json['details'] as Map<String, dynamic>?,
  );

  @override
  String toString() => 'ApiError($error: $message)';
}
