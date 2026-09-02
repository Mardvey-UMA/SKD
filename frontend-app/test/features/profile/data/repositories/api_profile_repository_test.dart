import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/errors/app_failures.dart';
import 'package:frontend_app/features/profile/data/repositories/api_profile_repository.dart';

class _StubDio implements Dio {
  _StubDio(this._response);

  final Map<String, dynamic> _response;
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
    return Response<T>(
      data: _response as T,
      statusCode: 200,
      requestOptions: RequestOptions(path: path),
    );
  }

  @override
  Future<Response<T>> put<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    lastPath = path;
    lastMethod = 'PUT';
    lastBody = data as Map<String, dynamic>?;
    return Response<T>(
      data: _response as T,
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
  Future<Response<T>> put<T>(String path, {Object? data, Map<String, dynamic>? queryParameters, Options? options, CancelToken? cancelToken, ProgressCallback? onSendProgress, ProgressCallback? onReceiveProgress}) async => throw _makeError(path);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

final _profileJson = <String, dynamic>{
  'id': 'u1',
  'email': 'user@test.com',
  'display_name': 'Test User',
  'avatar_url': null,
  'subscription_tier': 'free',
  'onboarding_completed': true,
  'created_at': '2026-01-01T00:00:00.000Z',
};

void main() {
  group('ApiProfileRepository', () {
    group('getUserProfile', () {
      test('calls GET /api/users/me', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        await repo.getUserProfile();
        expect(dio.lastPath, '/api/users/me');
        expect(dio.lastMethod, 'GET');
      });

      test('maps response JSON to UserProfile domain model', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        final profile = await repo.getUserProfile();
        expect(profile.id, 'u1');
        expect(profile.email, 'user@test.com');
        expect(profile.displayName, 'Test User');
        expect(profile.subscriptionTier, 'free');
        expect(profile.onboardingCompleted, isTrue);
      });

      test('maps 500 to ServerFailure after retries', () async {
        final repo = ApiProfileRepository(_ErrorDio(500));
        expect(() => repo.getUserProfile(), throwsA(isA<ServerFailure>()));
      });

      test('maps 401 to AuthFailure', () {
        final repo = ApiProfileRepository(_ErrorDio(401));
        expect(() => repo.getUserProfile(), throwsA(isA<AuthFailure>()));
      });
    });

    group('updateProfile', () {
      test('calls PUT /api/users/me', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        await repo.updateProfile(displayName: 'New Name');
        expect(dio.lastPath, '/api/users/me');
        expect(dio.lastMethod, 'PUT');
      });

      test('sends only non-null fields in body', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        await repo.updateProfile(displayName: 'Alice');
        expect(dio.lastBody, {'display_name': 'Alice'});
      });

      test('sends both fields when both provided', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        await repo.updateProfile(displayName: 'Alice', avatarUrl: 'https://example.com/a.jpg');
        expect(dio.lastBody, {
          'display_name': 'Alice',
          'avatar_url': 'https://example.com/a.jpg',
        });
      });

      test('sends empty body when no fields provided', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        await repo.updateProfile();
        expect(dio.lastBody, <String, dynamic>{});
      });

      test('maps response to updated UserProfile', () async {
        final dio = _StubDio(_profileJson);
        final repo = ApiProfileRepository(dio);
        final result = await repo.updateProfile(displayName: 'Alice');
        expect(result.displayName, 'Test User'); // from stub response
      });

      test('maps 500 to ServerFailure', () {
        final repo = ApiProfileRepository(_ErrorDio(500));
        expect(
          () => repo.updateProfile(displayName: 'X'),
          throwsA(isA<ServerFailure>()),
        );
      });

      test('maps 401 to AuthFailure', () {
        final repo = ApiProfileRepository(_ErrorDio(401));
        expect(
          () => repo.updateProfile(),
          throwsA(isA<AuthFailure>()),
        );
      });
    });
  });
}
