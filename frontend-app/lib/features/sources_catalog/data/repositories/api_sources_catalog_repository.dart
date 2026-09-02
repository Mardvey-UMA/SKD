import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../domain/entities/catalog_page.dart';
import '../../domain/repositories/i_sources_catalog_repository.dart';
import '../dtos/catalog_page_dto.dart';

class ApiSourcesCatalogRepository implements ISourcesCatalogRepository {
  const ApiSourcesCatalogRepository(this._dio);

  final Dio _dio;

  @override
  Future<CatalogPage> fetchCatalog({
    SourceType? type,
    String? q,
    String? cursor,
    int limit = 20,
  }) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/sources',
        queryParameters: {
          if (type != null) 'type': type.wire,
          if (q != null && q.isNotEmpty) 'q': q,
          if (cursor != null) 'cursor': cursor,
          'limit': limit,
        },
      );
      return CatalogPageDto.fromJson(response.data ?? const {}).toDomain();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
