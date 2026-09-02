import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/errors/app_failures.dart';
import 'package:frontend_app/features/collections/data/repositories/api_collections_repository.dart';
class _StubDio implements Dio {
  _StubDio({required this.response});

  final Map<String, dynamic> response;

  String? lastPath;
  String? lastMethod;
  Map<String, dynamic>? lastQueryParams;

  @override
  Future<Response<T>> get<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async {
    lastPath = path;
    lastMethod = 'GET';
    lastQueryParams = queryParameters;
    return Response<T>(
      data: response as T,
      statusCode: 200,
      requestOptions: RequestOptions(path: path),
    );
  }

  @override
  Future<Response<T>> post<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    lastPath = path;
    lastMethod = 'POST';
    return Response<T>(
      data: null,
      statusCode: 200,
      requestOptions: RequestOptions(path: path),
    );
  }

  @override
  Future<Response<T>> delete<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    lastPath = path;
    lastMethod = 'DELETE';
    return Response<T>(
      data: null,
      statusCode: 200,
      requestOptions: RequestOptions(path: path),
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _ErrorDio implements Dio {
  _ErrorDio(this.statusCode);

  final int statusCode;
  String? lastPath;
  String? lastMethod;

  DioException _makeError(String path) => DioException(
        type: DioExceptionType.badResponse,
        requestOptions: RequestOptions(path: path),
        response: Response(
          statusCode: statusCode,
          requestOptions: RequestOptions(path: path),
          data: <String, dynamic>{'error': 'err', 'message': 'msg'},
        ),
      );

  @override
  Future<Response<T>> get<T>(String path, {Object? data, Map<String, dynamic>? queryParameters, Options? options, CancelToken? cancelToken, ProgressCallback? onReceiveProgress}) async {
    lastPath = path;
    lastMethod = 'GET';
    throw _makeError(path);
  }

  @override
  Future<Response<T>> post<T>(String path, {Object? data, Map<String, dynamic>? queryParameters, Options? options, CancelToken? cancelToken, ProgressCallback? onSendProgress, ProgressCallback? onReceiveProgress}) async {
    lastPath = path;
    lastMethod = 'POST';
    throw _makeError(path);
  }

  @override
  Future<Response<T>> delete<T>(String path, {Object? data, Map<String, dynamic>? queryParameters, Options? options, CancelToken? cancelToken}) async {
    lastPath = path;
    lastMethod = 'DELETE';
    throw _makeError(path);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _pageResponse = <String, dynamic>{
  'items': <dynamic>[],
  'cursor': null,
  'hasNext': false,
};

const _pageWithCursor = <String, dynamic>{
  'items': <dynamic>[],
  'cursor': 'tok_abc',
  'hasNext': true,
};

void main() {
  group('ApiCollectionsRepository', () {
    group('getBookmarks', () {
      test('calls GET /api/feed/bookmarks', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.getBookmarks();
        expect(dio.lastPath, '/api/feed/bookmarks');
        expect(dio.lastMethod, 'GET');
      });

      test('passes cursor query param when provided', () async {
        final dio = _StubDio(response: _pageWithCursor);
        final repo = ApiCollectionsRepository(dio);
        await repo.getBookmarks(cursor: 'tok_1');
        expect(dio.lastQueryParams, {'cursor': 'tok_1'});
      });

      test('no query params when cursor is null', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.getBookmarks();
        expect(dio.lastQueryParams, isNull);
      });

      test('returns PaginatedContent with hasNext from response', () async {
        final dio = _StubDio(response: _pageWithCursor);
        final repo = ApiCollectionsRepository(dio);
        final result = await repo.getBookmarks();
        expect(result.hasNext, isTrue);
        expect(result.cursor, 'tok_abc');
      });

      test('maps 404 to NotFoundFailure', () async {
        final repo = ApiCollectionsRepository(_ErrorDio(404));
        expect(() => repo.getBookmarks(), throwsA(isA<NotFoundFailure>()));
      });

      test('maps 500 to ServerFailure', () async {
        final repo = ApiCollectionsRepository(_ErrorDio(500));
        expect(() => repo.getBookmarks(), throwsA(isA<ServerFailure>()));
      });
    });

    group('getLikes', () {
      test('calls GET /api/feed/likes', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.getLikes();
        expect(dio.lastPath, '/api/feed/likes');
      });

      test('maps 500 to ServerFailure', () async {
        final repo = ApiCollectionsRepository(_ErrorDio(500));
        expect(() => repo.getLikes(), throwsA(isA<ServerFailure>()));
      });
    });

    group('getDislikes', () {
      test('calls GET /api/feed/dislikes', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.getDislikes();
        expect(dio.lastPath, '/api/feed/dislikes');
      });
    });

    group('addBookmark', () {
      test('calls POST /api/feed/bookmarks/{id}', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.addBookmark('c1');
        expect(dio.lastPath, '/api/feed/bookmarks/c1');
        expect(dio.lastMethod, 'POST');
      });

      test('maps 500 to ServerFailure', () async {
        final repo = ApiCollectionsRepository(_ErrorDio(500));
        expect(() => repo.addBookmark('c1'), throwsA(isA<ServerFailure>()));
      });
    });

    group('removeBookmark', () {
      test('calls DELETE /api/feed/bookmarks/{id}', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.removeBookmark('c1');
        expect(dio.lastPath, '/api/feed/bookmarks/c1');
        expect(dio.lastMethod, 'DELETE');
      });
    });

    group('addLike', () {
      test('calls POST /api/feed/likes/{id}', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.addLike('c2');
        expect(dio.lastPath, '/api/feed/likes/c2');
        expect(dio.lastMethod, 'POST');
      });
    });

    group('removeLike', () {
      test('calls DELETE /api/feed/likes/{id}', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.removeLike('c2');
        expect(dio.lastPath, '/api/feed/likes/c2');
        expect(dio.lastMethod, 'DELETE');
      });
    });

    group('addDislike', () {
      test('calls POST /api/feed/dislikes/{id}', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.addDislike('c3');
        expect(dio.lastPath, '/api/feed/dislikes/c3');
        expect(dio.lastMethod, 'POST');
      });
    });

    group('removeDislike', () {
      test('calls DELETE /api/feed/dislikes/{id}', () async {
        final dio = _StubDio(response: _pageResponse);
        final repo = ApiCollectionsRepository(dio);
        await repo.removeDislike('c3');
        expect(dio.lastPath, '/api/feed/dislikes/c3');
        expect(dio.lastMethod, 'DELETE');
      });
    });
  });
}
