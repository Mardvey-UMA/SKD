import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../domain/entities/blocked_source.dart';
import '../../domain/repositories/i_blocked_sources_repository.dart';
import '../dtos/blocked_source_dto.dart';

class ApiBlockedSourcesRepository implements IBlockedSourcesRepository {
  const ApiBlockedSourcesRepository(this._dio);

  final Dio _dio;

  @override
  Future<List<BlockedSource>> list() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/blocked-sources',
      );
      final data = response.data ?? const {};
      final items = (data['items'] as List<dynamic>? ?? const [])
          .map((e) => BlockedSourceDto.fromJson(e as Map<String, dynamic>)
              .toDomain())
          .toList();
      return items;
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> block(String sourceId) async {
    try {
      await _dio.post<void>(
        '/api/feed/blocked-sources',
        data: {'source_id': sourceId},
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> unblock(String sourceId) async {
    try {
      await _dio.delete<void>('/api/feed/blocked-sources/$sourceId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
