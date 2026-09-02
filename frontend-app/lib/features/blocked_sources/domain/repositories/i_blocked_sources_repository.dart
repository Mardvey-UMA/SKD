import '../entities/blocked_source.dart';

abstract interface class IBlockedSourcesRepository {
  Future<List<BlockedSource>> list();
  Future<void> block(String sourceId);
  Future<void> unblock(String sourceId);
}
