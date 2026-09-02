import '../../../../core/sources/domain/source_type.dart';
import '../../domain/entities/blocked_source.dart';

class BlockedSourceDto {
  const BlockedSourceDto({
    required this.sourceId,
    required this.type,
    required this.name,
    required this.blockedAt,
  });

  final String sourceId;
  final String? type;
  final String? name;
  final String? blockedAt;

  factory BlockedSourceDto.fromJson(Map<String, dynamic> json) {
    return BlockedSourceDto(
      sourceId: (json['source_id'] ?? json['sourceId']) as String,
      type: (json['type'] ?? json['source_type']) as String?,
      name: (json['name'] ?? json['source_name']) as String?,
      blockedAt: (json['blocked_at'] ?? json['blockedAt']) as String?,
    );
  }

  BlockedSource toDomain() => BlockedSource(
    sourceId: sourceId,
    type: SourceType.fromWire(type),
    name: (name?.isNotEmpty ?? false) ? name : null,
    blockedAt: DateTime.tryParse(blockedAt ?? '') ?? DateTime.now(),
  );
}
