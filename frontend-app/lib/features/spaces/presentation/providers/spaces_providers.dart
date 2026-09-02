import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/sources/domain/space_color.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../data/repositories/api_spaces_repository.dart';
import '../../domain/entities/space.dart';
import '../../domain/entities/space_feed_page.dart';
import '../../domain/repositories/i_spaces_repository.dart';

final spacesRepositoryProvider = Provider<ISpacesRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiSpacesRepository(dio);
});

/// List of the current user's spaces. keepAlive=true so the SpacesScreen count
/// stays in sync across navigation.
class SpacesListNotifier extends AsyncNotifier<List<Space>> {
  @override
  Future<List<Space>> build() async {
    return ref.watch(spacesRepositoryProvider).listMySpaces();
  }

  Future<Space> createSpace({
    required String name,
    required SpaceColor color,
    required List<String> sourceIds,
  }) async {
    final prev = state.valueOrNull ?? const [];
    try {
      final space = await ref.read(spacesRepositoryProvider).createSpace(
            name: name,
            color: color,
            sourceIds: sourceIds,
          );
      state = AsyncData([...prev, space]);
      return space;
    } catch (e, st) {
      Error.throwWithStackTrace(e, st);
    }
  }

  Future<Space> updateSpace(
    String id, {
    String? name,
    SpaceColor? color,
    List<String>? sourceIds,
  }) async {
    final prev = state.valueOrNull ?? const [];
    try {
      final updated = await ref.read(spacesRepositoryProvider).updateSpace(
            id,
            name: name,
            color: color,
            sourceIds: sourceIds,
          );
      state = AsyncData(
        prev.map((s) => s.id == id ? updated : s).toList(),
      );
      return updated;
    } catch (e, st) {
      Error.throwWithStackTrace(e, st);
    }
  }

  Future<void> deleteSpace(String id) async {
    final prev = state.valueOrNull ?? const [];
    state = AsyncData(prev.where((s) => s.id != id).toList());
    try {
      await ref.read(spacesRepositoryProvider).deleteSpace(id);
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

final spacesListNotifierProvider =
    AsyncNotifierProvider<SpacesListNotifier, List<Space>>(
  SpacesListNotifier.new,
);

final spaceByIdProvider = Provider.family<Space?, String>((ref, id) {
  final listAsync = ref.watch(spacesListNotifierProvider);
  return listAsync.valueOrNull?.where((s) => s.id == id).firstOrNull;
});

class SpaceFeedState {
  const SpaceFeedState({
    this.items = const [],
    this.cursor,
    this.hasNext = true,
    this.isLoadingMore = false,
  });

  final List items;
  final String? cursor;
  final bool hasNext;
  final bool isLoadingMore;

  SpaceFeedState copyWith({
    List? items,
    String? cursor,
    bool? hasNext,
    bool? isLoadingMore,
  }) => SpaceFeedState(
    items: items ?? this.items,
    cursor: cursor ?? this.cursor,
    hasNext: hasNext ?? this.hasNext,
    isLoadingMore: isLoadingMore ?? this.isLoadingMore,
  );
}

class SpaceFeedNotifier
    extends FamilyAsyncNotifier<SpaceFeedPage, String> {
  @override
  Future<SpaceFeedPage> build(String arg) async {
    return ref.watch(spacesRepositoryProvider).fetchSpaceFeed(arg);
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasNext || current.cursor == null) return;
    try {
      final page = await ref
          .read(spacesRepositoryProvider)
          .fetchSpaceFeed(arg, cursor: current.cursor);
      state = AsyncData(
        SpaceFeedPage(
          items: [...current.items, ...page.items],
          cursor: page.cursor,
          hasNext: page.hasNext,
        ),
      );
    } catch (_) {
      // keep current state
    }
  }

  Future<void> refresh() async {
    ref.invalidateSelf();
    await future;
  }
}

final spaceFeedNotifierProvider = AsyncNotifierProvider.family<
    SpaceFeedNotifier, SpaceFeedPage, String>(
  SpaceFeedNotifier.new,
);
