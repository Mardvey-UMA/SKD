import '../../domain/models/feed_source.dart';

/// DTO stub for FeedSource — to be wired when backend is ready.
class FeedSourceDto {
  const FeedSourceDto({
    required this.id,
    required this.name,
    this.isSelected = false,
  });

  final String id;
  final String name;
  final bool isSelected;

  factory FeedSourceDto.fromJson(Map<String, dynamic> json) {
    return FeedSourceDto(
      id: json['id'] as String,
      name: json['name'] as String,
      isSelected: json['is_selected'] as bool? ?? false,
    );
  }

  FeedSource toDomain() {
    return FeedSource(id: id, name: name, isSelected: isSelected);
  }
}
