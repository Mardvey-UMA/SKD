import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/feed/data/repositories/api_feed_repository.dart';

/// Minimal Dio stub that returns a canned feed response with configurable
/// response headers. Uses noSuchMethod to satisfy remaining Dio interface.
class _StubDio implements Dio {
  _StubDio({required this.responseHeaders});

  final Map<String, List<String>> responseHeaders;

  @override
  Future<Response<T>> get<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async {
    return Response<T>(
      data:
          <String, dynamic>{
                'items': <dynamic>[],
                'cursor': null,
                'hasNext': false,
              }
              as T,
      statusCode: 200,
      requestOptions: RequestOptions(path: path),
      headers: Headers.fromMap(responseHeaders),
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  group('ApiFeedRepository X-Request-Id header capture', () {
    test(
      'getFeed with x-request-id header populates FeedPage.requestId',
      () async {
        const uuid = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
        final dio = _StubDio(
          responseHeaders: {
            'x-request-id': [uuid],
          },
        );
        final repo = ApiFeedRepository(dio);

        final page = await repo.getFeed();

        expect(page.requestId, uuid);
      },
    );

    test(
      'getFeed without x-request-id header leaves FeedPage.requestId null',
      () async {
        final dio = _StubDio(responseHeaders: {});
        final repo = ApiFeedRepository(dio);

        final page = await repo.getFeed();

        expect(page.requestId, isNull);
      },
    );

    test('getFeed does not throw when x-request-id header absent', () async {
      final dio = _StubDio(responseHeaders: {});
      final repo = ApiFeedRepository(dio);

      await expectLater(repo.getFeed(), completes);
    });
  });
}
