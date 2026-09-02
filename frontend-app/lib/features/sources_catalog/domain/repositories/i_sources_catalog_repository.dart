import '../../../../core/sources/domain/source_type.dart';
import '../entities/catalog_page.dart';

abstract interface class ISourcesCatalogRepository {
  Future<CatalogPage> fetchCatalog({
    SourceType? type,
    String? q,
    String? cursor,
    int limit = 20,
  });
}
