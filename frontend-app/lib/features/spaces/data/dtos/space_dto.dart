import '../../../../core/sources/domain/space_color.dart';
import '../../domain/entities/space.dart';

class SpaceDto {
  const SpaceDto({
    required this.id,
    required this.name,
    required this.color,
    required this.sourceIds,
    this.sourceCount,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String name;
  final String color;
  final List<String> sourceIds;
  final int? sourceCount;
  final String? createdAt;
  final String? updatedAt;

  factory SpaceDto.fromJson(Map<String, dynamic> json) {
    return SpaceDto(
      id: json['id'] as String,
      name: (json['name'] as String?) ?? '',
      color: (json['color'] as String?) ?? 'BLUE',
      sourceIds: ((json['source_ids'] ?? json['sourceIds']) as List<dynamic>?)
              ?.whereType<String>()
              .toList() ??
          const [],
      sourceCount: (json['source_count'] ?? json['sourceCount']) as int?,
      createdAt: (json['created_at'] ?? json['createdAt']) as String?,
      updatedAt: (json['updated_at'] ?? json['updatedAt']) as String?,
    );
  }

  Space toDomain() => Space(
    id: id,
    name: name,
    color: SpaceColor.fromWire(color),
    sourceIds: sourceIds,
    sourceCount: sourceCount ?? sourceIds.length,
    createdAt: DateTime.tryParse(createdAt ?? '') ?? DateTime.now(),
    updatedAt: DateTime.tryParse(updatedAt ?? '') ?? DateTime.now(),
  );
}
