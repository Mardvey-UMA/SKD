import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../../feed/data/dto/content_item_dto.dart';
import '../../domain/repositories/i_collections_repository.dart';

class ApiCollectionsRepository implements ICollectionsRepository {
  const ApiCollectionsRepository(this._dio);

  final Dio _dio;

  PaginatedContent _parsePage(Map<String, dynamic> data) {
    final items = (data['items'] as List<dynamic>)
        .map(
          (e) => ContentItemDto.fromJson(e as Map<String, dynamic>).toDomain(),
        )
        .toList();
    return PaginatedContent(
      items: items,
      cursor: data['cursor'] as String?,
      hasNext: data['hasNext'] as bool? ?? false,
    );
  }

  @override
  Future<PaginatedContent> getBookmarks({String? cursor}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/bookmarks',
        queryParameters: cursor != null ? {'cursor': cursor} : null,
      );
      return _parsePage(response.data!);
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<PaginatedContent> getLikes({String? cursor}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/likes',
        queryParameters: cursor != null ? {'cursor': cursor} : null,
      );
      return _parsePage(response.data!);
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<PaginatedContent> getDislikes({String? cursor}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/dislikes',
        queryParameters: cursor != null ? {'cursor': cursor} : null,
      );
      return _parsePage(response.data!);
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> addBookmark(String contentId) async {
    try {
      await _dio.post<void>('/api/feed/bookmarks/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> removeBookmark(String contentId) async {
    try {
      await _dio.delete<void>('/api/feed/bookmarks/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> addLike(String contentId) async {
    try {
      await _dio.post<void>('/api/feed/likes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> removeLike(String contentId) async {
    try {
      await _dio.delete<void>('/api/feed/likes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> addDislike(String contentId) async {
    try {
      await _dio.post<void>('/api/feed/dislikes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> removeDislike(String contentId) async {
    try {
      await _dio.delete<void>('/api/feed/dislikes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
