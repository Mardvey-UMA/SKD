import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/errors/app_failures.dart';
import 'package:frontend_app/features/onboarding/data/repositories/api_onboarding_repository.dart';

class _StubDio implements Dio {
  _StubDio(this._responses);

  final List<Map<String, dynamic>> _responses;
  int _callCount = 0;
  String? lastPath;
  String? lastMethod;
  Map<String, dynamic>? lastBody;

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
    final resp = _responses[_callCount++ % _responses.length];
    return Response<T>(
      data: resp as T,
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
    lastBody = data as Map<String, dynamic>?;
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
  Future<Response<T>> get<T>(String path, {Object? data, Map<String, dynamic>? queryParameters, Options? options, CancelToken? cancelToken, ProgressCallback? onReceiveProgress}) async => throw _makeError(path);

  @override
  Future<Response<T>> post<T>(String path, {Object? data, Map<String, dynamic>? queryParameters, Options? options, CancelToken? cancelToken, ProgressCallback? onSendProgress, ProgressCallback? onReceiveProgress}) async => throw _makeError(path);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

final _categoriesResponse = <String, dynamic>{
  'categories': <dynamic>[
    {'id': 'tech', 'name': 'Technology', 'icon': 'laptop'},
    {'id': 'sport', 'name': 'Sports', 'icon': 'sports'},
  ],
  'min_select': 3,
  'max_select': 5,
};

final _profileResponse = <String, dynamic>{
  'id': 'u1',
  'email': 'user@test.com',
  'display_name': null,
  'avatar_url': null,
  'subscription_tier': 'free',
  'onboarding_completed': true,
  'created_at': '2026-01-01T00:00:00.000Z',
};

final _profileIncomplete = <String, dynamic>{
  ..._profileResponse,
  'onboarding_completed': false,
};

void main() {
  group('ApiOnboardingRepository', () {
    group('getCategories', () {
      test('calls GET /api/users/me/categories', () async {
        final dio = _StubDio([_categoriesResponse]);
        final repo = ApiOnboardingRepository(dio);
        await repo.getCategories();
        expect(dio.lastPath, '/api/users/me/categories');
        expect(dio.lastMethod, 'GET');
      });

      test('parses categories, minSelect, maxSelect from response', () async {
        final dio = _StubDio([_categoriesResponse]);
        final repo = ApiOnboardingRepository(dio);
        final result = await repo.getCategories();
        expect(result.categories.length, 2);
        expect(result.categories.first.id, 'tech');
        expect(result.categories.first.name, 'Technology');
        expect(result.minSelect, 3);
        expect(result.maxSelect, 5);
      });

      test('maps 500 to ServerFailure', () {
        final repo = ApiOnboardingRepository(_ErrorDio(500));
        expect(() => repo.getCategories(), throwsA(isA<ServerFailure>()));
      });

      test('maps 401 to AuthFailure', () {
        final repo = ApiOnboardingRepository(_ErrorDio(401));
        expect(() => repo.getCategories(), throwsA(isA<AuthFailure>()));
      });
    });

    group('completeOnboarding', () {
      test('calls POST /api/users/me/onboarding with correct body', () async {
        final dio = _StubDio([_profileResponse]);
        final repo = ApiOnboardingRepository(dio);
        await repo.completeOnboarding(['tech', 'sport'], ['src1']);
        expect(dio.lastPath, '/api/users/me/onboarding');
        expect(dio.lastMethod, 'POST');
        expect(dio.lastBody, {
          'categories': ['tech', 'sport'],
          'source_content_ids': ['src1'],
        });
      });

      test('maps 500 to ServerFailure', () {
        final repo = ApiOnboardingRepository(_ErrorDio(500));
        expect(
          () => repo.completeOnboarding(['tech'], []),
          throwsA(isA<ServerFailure>()),
        );
      });
    });

    group('isOnboardingCompleted', () {
      test('returns true when onboarding_completed is true', () async {
        final dio = _StubDio([_profileResponse]);
        final repo = ApiOnboardingRepository(dio);
        final result = await repo.isOnboardingCompleted();
        expect(result, isTrue);
      });

      test('returns false when onboarding_completed is false', () async {
        final dio = _StubDio([_profileIncomplete]);
        final repo = ApiOnboardingRepository(dio);
        final result = await repo.isOnboardingCompleted();
        expect(result, isFalse);
      });

      test('maps non-404 error to failure', () {
        final repo = ApiOnboardingRepository(_ErrorDio(500));
        expect(() => repo.isOnboardingCompleted(), throwsA(isA<ServerFailure>()));
      });
    });

    group('clearOnboarding', () {
      test('is a no-op and completes without error', () async {
        final dio = _StubDio([_profileResponse]);
        final repo = ApiOnboardingRepository(dio);
        await expectLater(repo.clearOnboarding(), completes);
      });
    });
  });
}
