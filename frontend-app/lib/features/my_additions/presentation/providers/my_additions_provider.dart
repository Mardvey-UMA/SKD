import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../data/repositories/api_my_additions_repository.dart';
import '../../domain/entities/source_addition.dart';
import '../../domain/repositories/i_my_additions_repository.dart';

final myAdditionsRepositoryProvider =
    Provider<IMyAdditionsRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiMyAdditionsRepository(dio);
});

class MyAdditionsState {
  const MyAdditionsState({
    this.items = const [],
    this.cursor,
    this.hasNext = false,
    this.isLoadingMore = false,
  });

  final List<SourceAddition> items;
  final String? cursor;
  final bool hasNext;
  final bool isLoadingMore;

  MyAdditionsState copyWith({
    List<SourceAddition>? items,
    String? cursor,
    bool? hasNext,
    bool? isLoadingMore,
  }) => MyAdditionsState(
    items: items ?? this.items,
    cursor: cursor ?? this.cursor,
    hasNext: hasNext ?? this.hasNext,
    isLoadingMore: isLoadingMore ?? this.isLoadingMore,
  );
}

class MyAdditionsNotifier extends AsyncNotifier<MyAdditionsState> {
  @override
  Future<MyAdditionsState> build() async {
    final page = await ref.watch(myAdditionsRepositoryProvider).list();
    return MyAdditionsState(
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
          .read(myAdditionsRepositoryProvider)
          .list(cursor: current.cursor);
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

final myAdditionsNotifierProvider =
    AsyncNotifierProvider<MyAdditionsNotifier, MyAdditionsState>(
  MyAdditionsNotifier.new,
);
