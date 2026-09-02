import '../../../sources_catalog/data/dtos/source_dto.dart';
import '../../domain/entities/add_source_result.dart';

class AddSourceResponseDto {
  const AddSourceResponseDto({
    required this.source,
    required this.wasExisting,
  });

  final SourceDto source;
  final bool wasExisting;

  factory AddSourceResponseDto.fromJson(Map<String, dynamic> json) {
    // Two-shape tolerance: backend may send {source, was_existing} or the source
    // fields directly (with an extra was_existing flag).
    final sourceJson = (json['source'] as Map<String, dynamic>?) ?? json;
    return AddSourceResponseDto(
      source: SourceDto.fromJson(sourceJson),
      wasExisting:
          (json['was_existing'] ?? json['wasExisting']) as bool? ?? false,
    );
  }

  AddSourceResult toDomain() =>
      AddSourceResult(source: source.toDomain(), wasExisting: wasExisting);
}
