import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../../feed/presentation/providers/feed_provider.dart';
import '../../data/repositories/api_blocked_sources_repository.dart';
import '../../domain/entities/blocked_source.dart';
import '../../domain/repositories/i_blocked_sources_repository.dart';

final blockedSourcesRepositoryProvider =
    Provider<IBlockedSourcesRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiBlockedSourcesRepository(dio);
});

class BlockedSourcesNotifier extends AsyncNotifier<List<BlockedSource>> {
  @override
  Future<List<BlockedSource>> build() async {
    final repo = ref.watch(blockedSourcesRepositoryProvider);
    return repo.list();
  }

  Future<void> block(
    String sourceId, {
    String? cachedName,
    SourceType? cachedType,
  }) async {
    final prev = state.valueOrNull ?? const [];
    if (prev.any((b) => b.sourceId == sourceId)) {
      return;
    }
    final optimistic = BlockedSource(
      sourceId: sourceId,
      type: cachedType,
      name: cachedName,
      blockedAt: DateTime.now(),
    );
    state = AsyncData([optimistic, ...prev]);
    try {
      await ref.read(blockedSourcesRepositoryProvider).block(sourceId);
    } catch (e, st) {
      state = AsyncData(prev);
      Error.throwWithStackTrace(e, st);
    }
  }

  Future<void> unblock(String sourceId) async {
    final prev = state.valueOrNull ?? const [];
    state = AsyncData(prev.where((s) => s.sourceId != sourceId).toList());
    try {
      await ref.read(blockedSourcesRepositoryProvider).unblock(sourceId);
      ref.invalidate(feedNotifierProvider);
    } catch (e, st) {
      state = AsyncData(prev);
      Error.throwWithStackTrace(e, st);
    }
  }

  Future<void> refresh() async {
    ref.invalidateSelf();
    await future;
  }
}

final blockedSourcesNotifierProvider =
    AsyncNotifierProvider<BlockedSourcesNotifier, List<BlockedSource>>(
  BlockedSourcesNotifier.new,
);
