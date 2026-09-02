import '../../../../core/sources/domain/source_type.dart';
import '../../domain/entities/source_addition.dart';

class SourceAdditionDto {
  const SourceAdditionDto({
    required this.sourceId,
    required this.type,
    required this.name,
    required this.addedAt,
  });

  final String sourceId;
  final String? type;
  final String? name;
  final String? addedAt;

  factory SourceAdditionDto.fromJson(Map<String, dynamic> json) {
    return SourceAdditionDto(
      sourceId: (json['source_id'] ?? json['sourceId']) as String,
      type: (json['source_type'] ?? json['type']) as String?,
      name: (json['source_name'] ?? json['name']) as String?,
      addedAt: (json['added_at'] ?? json['addedAt']) as String?,
    );
  }

  SourceAddition toDomain() => SourceAddition(
    sourceId: sourceId,
    type: SourceType.fromWire(type) ?? SourceType.telegram,
    name: name ?? '',
    addedAt: DateTime.tryParse(addedAt ?? '') ?? DateTime.now(),
  );
}
