import '../../domain/entities/catalog_page.dart';
import 'source_dto.dart';

class CatalogPageDto {
  const CatalogPageDto({
    required this.items,
    required this.cursor,
    required this.hasNext,
  });

  final List<SourceDto> items;
  final String? cursor;
  final bool hasNext;

  factory CatalogPageDto.fromJson(Map<String, dynamic> json) {
    // Backend currently ships `next_cursor` + `has_more`; older shape
    // was `cursor` + `has_next`/`hasNext`. Accept all three so a change
    // on either side doesn't break pagination.
    return CatalogPageDto(
      items: (json['items'] as List<dynamic>? ?? const [])
          .map((e) => SourceDto.fromJson(e as Map<String, dynamic>))
          .toList(),
      cursor: (json['next_cursor'] ?? json['cursor']) as String?,
      hasNext: (json['has_more'] ?? json['has_next'] ?? json['hasNext'])
              as bool? ??
          false,
    );
  }

  CatalogPage toDomain() => CatalogPage(
    items: items.map((e) => e.toDomain()).toList(),
    cursor: cursor,
    hasNext: hasNext,
  );
}
