import '../../../../core/sources/domain/source_type.dart';
import '../../domain/entities/source.dart';

class SourceDto {
  const SourceDto({
    required this.id,
    required this.type,
    required this.name,
    this.url,
    this.iconUrl,
    this.createdAt,
  });

  final String id;
  final String type;
  final String name;
  final String? url;
  final String? iconUrl;
  final String? createdAt;

  factory SourceDto.fromJson(Map<String, dynamic> json) {
    return SourceDto(
      id: json['id'] as String,
      type: (json['source_type'] ?? json['type']) as String? ?? '',
      name: (json['name'] as String?) ?? '',
      url: json['url'] as String?,
      iconUrl: json['icon_url'] as String?,
      createdAt: json['created_at'] as String?,
    );
  }

  Source toDomain() => Source(
    id: id,
    type: SourceType.fromWire(type) ?? SourceType.telegram,
    name: name,
    url: url,
    iconUrl: iconUrl,
    createdAt: createdAt != null ? DateTime.tryParse(createdAt!) : null,
  );
}
