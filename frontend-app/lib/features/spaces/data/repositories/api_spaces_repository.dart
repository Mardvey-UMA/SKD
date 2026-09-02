import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../../../core/sources/domain/space_color.dart';
import '../../../feed/data/dto/content_item_dto.dart';
import '../../domain/entities/space.dart';
import '../../domain/entities/space_feed_page.dart';
import '../../domain/repositories/i_spaces_repository.dart';
import '../dtos/space_dto.dart';

class ApiSpacesRepository implements ISpacesRepository {
  const ApiSpacesRepository(this._dio);

  final Dio _dio;

  @override
  Future<List<Space>> listMySpaces() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/spaces',
      );
      final data = response.data ?? const {};
      return (data['items'] as List<dynamic>? ?? const [])
          .map((e) => SpaceDto.fromJson(e as Map<String, dynamic>).toDomain())
          .toList();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<Space> createSpace({
    required String name,
    required SpaceColor color,
    required List<String> sourceIds,
  }) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/feed/spaces',
        data: {
          'name': name,
          'color': color.wire,
          'source_ids': sourceIds,
        },
      );
      return SpaceDto.fromJson(response.data ?? const {}).toDomain();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<Space> updateSpace(
    String id, {
    String? name,
    SpaceColor? color,
    List<String>? sourceIds,
  }) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/feed/spaces/$id',
        data: {
          if (name != null) 'name': name,
          if (color != null) 'color': color.wire,
          if (sourceIds != null) 'source_ids': sourceIds,
        },
      );
      return SpaceDto.fromJson(response.data ?? const {}).toDomain();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> deleteSpace(String id) async {
    try {
      await _dio.delete<void>('/api/feed/spaces/$id');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<SpaceFeedPage> fetchSpaceFeed(
    String id, {
    String? cursor,
    int limit = 20,
  }) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/spaces/$id/items',
        queryParameters: {
          if (cursor != null) 'cursor': cursor,
          'limit': limit,
        },
      );
      final data = response.data ?? const {};
      final items = (data['items'] as List<dynamic>? ?? const [])
          .map((e) =>
              ContentItemDto.fromJson(e as Map<String, dynamic>).toDomain())
          .toList();
      return SpaceFeedPage(
        items: items,
        cursor: data['cursor'] as String?,
        hasNext: (data['has_next'] ?? data['hasNext']) as bool? ?? false,
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
