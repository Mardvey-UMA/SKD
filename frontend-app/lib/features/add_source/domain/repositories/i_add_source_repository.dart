import '../../../../core/sources/domain/source_type.dart';
import '../entities/add_source_result.dart';

abstract interface class IAddSourceRepository {
  Future<AddSourceResult> addSource({
    required SourceType type,
    required Map<String, dynamic> params,
  });
}
