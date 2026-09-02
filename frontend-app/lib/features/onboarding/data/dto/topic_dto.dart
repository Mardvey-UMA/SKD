import '../../domain/models/topic.dart';

class TopicDto {
  const TopicDto({required this.id, required this.name, required this.icon});

  final String id;
  final String name;
  final String icon;

  factory TopicDto.fromJson(Map<String, dynamic> json) => TopicDto(
    id: json['id'] as String,
    name: json['name'] as String,
    icon: json['icon'] as String,
  );

  Topic toDomain() => Topic(id: id, name: name, icon: icon);
}
