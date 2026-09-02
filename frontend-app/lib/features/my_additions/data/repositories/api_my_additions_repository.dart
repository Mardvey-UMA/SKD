import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../domain/entities/source_addition.dart';
import '../../domain/repositories/i_my_additions_repository.dart';
import '../dtos/source_addition_dto.dart';

class ApiMyAdditionsRepository implements IMyAdditionsRepository {
  const ApiMyAdditionsRepository(this._dio);

  final Dio _dio;

  @override
  Future<MyAdditionsPage> list({String? cursor, int limit = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/my-additions',
        queryParameters: {
          if (cursor != null) 'cursor': cursor,
          'limit': limit,
        },
      );
      final data = response.data ?? const {};
      final items = (data['items'] as List<dynamic>? ?? const [])
          .map((e) =>
              SourceAdditionDto.fromJson(e as Map<String, dynamic>).toDomain())
          .toList();
      return MyAdditionsPage(
        items: items,
        cursor: data['cursor'] as String?,
        hasNext: (data['has_next'] ?? data['hasNext']) as bool? ?? false,
      );
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
