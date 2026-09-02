import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../data/repositories/api_sources_catalog_repository.dart';
import '../../domain/entities/source.dart';
import '../../domain/repositories/i_sources_catalog_repository.dart';
import '../../domain/value_objects/catalog_query.dart';

final sourcesCatalogRepositoryProvider =
    Provider<ISourcesCatalogRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiSourcesCatalogRepository(dio);
});

class SourcesCatalogState {
  const SourcesCatalogState({
    this.items = const [],
    this.cursor,
    this.hasNext = false,
    this.isLoadingMore = false,
  });

  final List<Source> items;
  final String? cursor;
  final bool hasNext;
  final bool isLoadingMore;

  SourcesCatalogState copyWith({
    List<Source>? items,
    String? cursor,
    bool? hasNext,
    bool? isLoadingMore,
    bool clearCursor = false,
  }) => SourcesCatalogState(
    items: items ?? this.items,
    cursor: clearCursor ? null : (cursor ?? this.cursor),
    hasNext: hasNext ?? this.hasNext,
    isLoadingMore: isLoadingMore ?? this.isLoadingMore,
  );
}

class SourcesCatalogNotifier
    extends FamilyAsyncNotifier<SourcesCatalogState, CatalogQuery> {
  @override
  Future<SourcesCatalogState> build(CatalogQuery arg) async {
    final repo = ref.watch(sourcesCatalogRepositoryProvider);
    final page = await repo.fetchCatalog(type: arg.type, q: arg.q);
    return SourcesCatalogState(
      items: page.items,
      cursor: page.cursor,
      hasNext: page.hasNext,
    );
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || current.isLoadingMore || !current.hasNext) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    try {
      final page = await ref
          .read(sourcesCatalogRepositoryProvider)
          .fetchCatalog(type: arg.type, q: arg.q, cursor: current.cursor);
      state = AsyncData(
        current.copyWith(
          items: [...current.items, ...page.items],
          cursor: page.cursor,
          hasNext: page.hasNext,
          isLoadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(isLoadingMore: false));
    }
  }

  Future<void> refresh() async {
    ref.invalidateSelf();
    await future;
  }
}

final sourcesCatalogNotifierProvider = AsyncNotifierProvider.family<
    SourcesCatalogNotifier, SourcesCatalogState, CatalogQuery>(
  SourcesCatalogNotifier.new,
);
