import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../domain/models/content_item.dart';
import '../../domain/models/feed_state.dart';
import '../../domain/repositories/i_feed_repository.dart';
import '../dto/content_item_dto.dart';

class ApiFeedRepository implements IFeedRepository {
  ApiFeedRepository(this._dio);

  final Dio _dio;

  @override
  Future<FeedPage> getFeed({String? cursor, bool refresh = false}) async {
    try {
      final queryParams = <String, dynamic>{
        'cursor': cursor,
        if (refresh) 'refresh': true,
        // Cache-busting: prevent browser from returning cached XHR responses
        // for repeated GET requests with the same cursor on Flutter Web.
        '_t': DateTime.now().millisecondsSinceEpoch,
      }..removeWhere((_, v) => v == null);

      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed',
        queryParameters: queryParams,
      );

      final data = response.data!;
      final items = (data['items'] as List<dynamic>)
          .map(
            (e) =>
                ContentItemDto.fromJson(e as Map<String, dynamic>).toDomain(),
          )
          .toList();

      return FeedPage(
        items: items,
        cursor: data['cursor'] as String?,
        hasNext: data['hasNext'] as bool? ?? false,
        // Capture X-Request-Id for interaction event attribution.
        requestId: response.headers.value('x-request-id'),
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<ContentItem> getContentItem(String contentId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/content/$contentId',
      );
      return ContentItemDto.fromJson(response.data!).toDomain();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<ContentInteractionStatus> getContentStatus(String contentId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/content/$contentId/status',
      );
      return ContentInteractionStatus.fromJson(response.data!);
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> likeContent(String contentId) async {
    try {
      await _dio.post<void>('/api/feed/likes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> unlikeContent(String contentId) async {
    try {
      await _dio.delete<void>('/api/feed/likes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> dislikeContent(String contentId) async {
    try {
      await _dio.post<void>('/api/feed/dislikes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> undislikeContent(String contentId) async {
    try {
      await _dio.delete<void>('/api/feed/dislikes/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> bookmarkContent(String contentId) async {
    try {
      await _dio.post<void>('/api/feed/bookmarks/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> unbookmarkContent(String contentId) async {
    try {
      await _dio.delete<void>('/api/feed/bookmarks/$contentId');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
