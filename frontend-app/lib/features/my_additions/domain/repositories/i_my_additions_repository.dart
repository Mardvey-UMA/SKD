import '../entities/source_addition.dart';

abstract interface class IMyAdditionsRepository {
  Future<MyAdditionsPage> list({String? cursor, int limit = 20});
}
