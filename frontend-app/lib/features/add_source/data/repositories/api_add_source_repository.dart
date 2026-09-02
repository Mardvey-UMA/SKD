import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../domain/entities/add_source_result.dart';
import '../../domain/repositories/i_add_source_repository.dart';
import '../dtos/add_source_response_dto.dart';

class ApiAddSourceRepository implements IAddSourceRepository {
  const ApiAddSourceRepository(this._dio);

  final Dio _dio;

  @override
  Future<AddSourceResult> addSource({
    required SourceType type,
    required Map<String, dynamic> params,
  }) async {
    final path = switch (type) {
      SourceType.telegram => '/api/config/v1/sources/telegram',
      SourceType.habr => '/api/config/v1/sources/habr',
      SourceType.vcRu => '/api/config/v1/sources/vcru',
    };
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        path,
        data: params,
      );
      final data = response.data ?? const {};
      final wasExisting = response.statusCode == 200 ||
          (data['was_existing'] as bool? ?? false);
      final dto = AddSourceResponseDto.fromJson({
        ...data,
        'was_existing': wasExisting,
      });
      return dto.toDomain();
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
