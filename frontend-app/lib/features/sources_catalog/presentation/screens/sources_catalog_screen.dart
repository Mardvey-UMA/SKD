import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../subscription/presentation/providers/subscription_provider.dart';
import '../../domain/entities/source.dart';
import '../../domain/value_objects/catalog_query.dart';
import '../providers/sources_catalog_provider.dart';
import '../widgets/catalog_filter_chips.dart';
import '../widgets/catalog_search_bar.dart';
import '../widgets/source_card.dart';

class SourcesCatalogScreen extends ConsumerStatefulWidget {
  const SourcesCatalogScreen({super.key, this.highlightId});

  final String? highlightId;

  @override
  ConsumerState<SourcesCatalogScreen> createState() =>
      _SourcesCatalogScreenState();
}

class _SourcesCatalogScreenState extends ConsumerState<SourcesCatalogScreen> {
  SourceType? _type;
  String _query = '';
  late final ScrollController _scroll;

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
      final query = CatalogQuery(type: _type, q: _query);
      ref
          .read(sourcesCatalogNotifierProvider(query).notifier)
          .loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final query = CatalogQuery(type: _type, q: _query);
    final state = ref.watch(sourcesCatalogNotifierProvider(query));
    final subAsync = ref.watch(subscriptionNotifierProvider);
    final isPremium = subAsync.valueOrNull?.isPremium ?? false;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Источники'),
        backgroundColor: AppColors.background,
        elevation: 0,
        foregroundColor: AppColors.textPrimary,
        actions: [
          if (isPremium)
            IconButton(
              tooltip: 'Добавить свой источник',
              onPressed: () => context.push('/sources/add'),
              icon: const Icon(Icons.add),
            ),
        ],
      ),
      body: Column(
        children: [
          CatalogSearchBar(
            initialValue: _query,
            onChanged: (v) => setState(() => _query = v),
          ),
          CatalogFilterChips(
            selected: _type,
            onChanged: (t) => setState(() => _type = t),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: state.when(
              loading: () =>
                  const Center(child: CircularProgressIndicator()),
              error: (e, _) => _ErrorState(
                message: e.toString(),
                onRetry: () =>
                    ref.invalidate(sourcesCatalogNotifierProvider(query)),
              ),
              data: (value) => _buildList(value.items, value.isLoadingMore,
                  value.hasNext, query),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildList(
    List<Source> items,
    bool isLoadingMore,
    bool hasNext,
    CatalogQuery query,
  ) {
    if (items.isEmpty) {
      return const _EmptyState();
    }
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(sourcesCatalogNotifierProvider(query));
      },
      child: ListView.separated(
        controller: _scroll,
        padding: const EdgeInsets.only(bottom: 24),
        itemCount: items.length + 1,
        separatorBuilder: (_, __) => const Divider(
          height: 1,
          color: AppColors.surfaceLight,
          indent: 16,
          endIndent: 16,
        ),
        itemBuilder: (_, index) {
          if (index == items.length) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Center(
                child: isLoadingMore
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : hasNext
                        ? const SizedBox.shrink()
                        : const Text(
                            'Больше нет',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.textHint,
                            ),
                          ),
              ),
            );
          }
          final source = items[index];
          final highlighted = widget.highlightId == source.id;
          return Container(
            color: highlighted
                ? AppColors.itemCountBg
                : Colors.transparent,
            child: SourceCard(source: source),
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
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: const [
            Icon(Icons.inbox_outlined,
                size: 48, color: AppColors.textHint),
            SizedBox(height: 12),
            Text(
              'Ничего не найдено',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
            SizedBox(height: 4),
            Text(
              'Попробуйте изменить фильтр или поиск',
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

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline,
                size: 48, color: AppColors.error),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 12),
            TextButton(onPressed: onRetry, child: const Text('Повторить')),
          ],
        ),
      ),
    );
  }
}
