import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/config/feature_flags.dart';
import '../../../../core/theme/app_colors.dart';
import '../providers/my_additions_provider.dart';
import '../widgets/addition_tile.dart';

class MyAdditionsScreen extends ConsumerStatefulWidget {
  const MyAdditionsScreen({super.key});

  @override
  ConsumerState<MyAdditionsScreen> createState() => _MyAdditionsScreenState();
}

class _MyAdditionsScreenState extends ConsumerState<MyAdditionsScreen> {
  late final ScrollController _scroll;
  String _filter = '';

  @override
  void initState() {
    super.initState();
    _scroll = ScrollController()..addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    final pos = _scroll.position;
    if (pos.maxScrollExtent > 0 &&
        pos.pixels >= pos.maxScrollExtent * 0.8) {
      ref.read(myAdditionsNotifierProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(myAdditionsNotifierProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text('Мои добавленные'
            '${state.valueOrNull != null ? ' (${state.valueOrNull!.items.length} из ${FeatureFlags.maxAddedSources})' : ''}'),
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
      ),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                e.toString(),
                style: const TextStyle(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () =>
                    ref.invalidate(myAdditionsNotifierProvider),
                child: const Text('Повторить'),
              ),
            ],
          ),
        ),
        data: (value) {
          final filtered = _filter.isEmpty
              ? value.items
              : value.items
                  .where((a) =>
                      a.name.toLowerCase().contains(_filter.toLowerCase()))
                  .toList();
          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: TextField(
                  onChanged: (v) =>
                      setState(() => _filter = v.trim()),
                  decoration: InputDecoration(
                    hintText: 'Поиск по названию',
                    prefixIcon: const Icon(Icons.search),
                    filled: true,
                    fillColor: AppColors.surface,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide:
                          const BorderSide(color: AppColors.inputBorder),
                    ),
                  ),
                ),
              ),
              Expanded(
                child: filtered.isEmpty
                    ? const _EmptyState()
                    : RefreshIndicator(
                        onRefresh: () => ref
                            .read(myAdditionsNotifierProvider.notifier)
                            .refresh(),
                        child: ListView.separated(
                          controller: _scroll,
                          itemCount:
                              filtered.length + (value.hasNext ? 1 : 0),
                          separatorBuilder: (_, __) => const Divider(
                            height: 1,
                            color: AppColors.surfaceLight,
                          ),
                          itemBuilder: (_, i) {
                            if (i == filtered.length) {
                              return Padding(
                                padding:
                                    const EdgeInsets.symmetric(vertical: 12),
                                child: Center(
                                  child: value.isLoadingMore
                                      ? const SizedBox(
                                          width: 20,
                                          height: 20,
                                          child: CircularProgressIndicator(
                                              strokeWidth: 2),
                                        )
                                      : const SizedBox.shrink(),
                                ),
                              );
                            }
                            return AdditionTile(addition: filtered[i]);
                          },
                        ),
                      ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.bookmark_add_outlined,
                size: 48, color: AppColors.textHint),
            SizedBox(height: 12),
            Text(
              'Вы ещё не добавляли источники',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
            SizedBox(height: 4),
            Text(
              'Перейдите в Каталог и добавьте свой первый источник.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
