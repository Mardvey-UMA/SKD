import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../shared/widgets/icon_tile.dart';
import '../../../../shared/widgets/segmented_control.dart';
import '../../../feed/presentation/widgets/content_card_widget.dart';
import '../providers/collections_provider.dart';

class CollectionsScreen extends ConsumerStatefulWidget {
  const CollectionsScreen({super.key, this.initialTab = 0});

  /// 0 — bookmarks, 1 — likes, 2 — dislikes. Consumed once on first build.
  final int initialTab;

  @override
  ConsumerState<CollectionsScreen> createState() => _CollectionsScreenState();
}

class _CollectionsScreenState extends ConsumerState<CollectionsScreen> {
  late int _selectedTab = widget.initialTab.clamp(0, 2);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
              child: Row(
                children: [
                  const IconTile(
                    icon: Icons.collections_bookmark_rounded,
                    size: 32,
                    iconSize: 18,
                    variant: AccentVariant.violet,
                    borderRadius: 16,
                  ),
                  const SizedBox(width: 10),
                  const Text(
                    'Коллекции',
                    style: TextStyle(
                      fontFamily: 'Manrope',
                      fontSize: 22,
                      fontWeight: FontWeight.w600,
                      letterSpacing: -0.008 * 22,
                      color: Color(0xFF0E1525), // text.primary = neutral-900
                    ),
                  ),
                ],
              ),
            ),
            // Segmented control
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
              child: SegmentedControl(
                labels: const ['Закладки', 'Нравится', 'Не нравится'],
                icons: const [
                  Icons.bookmark_outline_rounded,
                  Icons.thumb_up_outlined,
                  Icons.thumb_down_outlined,
                ],
                selectedIndex: _selectedTab,
                onChanged: (i) => setState(() => _selectedTab = i),
              ),
            ),
            // Body
            Expanded(
              child: IndexedStack(
                index: _selectedTab,
                children: const [
                  _CollectionTabView(type: _TabType.bookmarks),
                  _CollectionTabView(type: _TabType.likes),
                  _CollectionTabView(type: _TabType.dislikes),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum _TabType { bookmarks, likes, dislikes }

class _CollectionTabView extends ConsumerStatefulWidget {
  const _CollectionTabView({required this.type});

  final _TabType type;

  @override
  ConsumerState<_CollectionTabView> createState() => _CollectionTabViewState();
}

class _CollectionTabViewState extends ConsumerState<_CollectionTabView> {
  late final ScrollController _scrollController;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController()..addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      _loadMore();
    }
  }

  void _loadMore() {
    switch (widget.type) {
      case _TabType.bookmarks:
        ref.read(bookmarksNotifierProvider.notifier).loadMore();
      case _TabType.likes:
        ref.read(likesNotifierProvider.notifier).loadMore();
      case _TabType.dislikes:
        ref.read(dislikesNotifierProvider.notifier).loadMore();
    }
  }

  Future<void> _refresh() {
    switch (widget.type) {
      case _TabType.bookmarks:
        return ref.read(bookmarksNotifierProvider.notifier).refresh();
      case _TabType.likes:
        return ref.read(likesNotifierProvider.notifier).refresh();
      case _TabType.dislikes:
        return ref.read(dislikesNotifierProvider.notifier).refresh();
    }
  }

  AsyncValue<CollectionTabState> _watchState() {
    return switch (widget.type) {
      _TabType.bookmarks => ref.watch(bookmarksNotifierProvider),
      _TabType.likes => ref.watch(likesNotifierProvider),
      _TabType.dislikes => ref.watch(dislikesNotifierProvider),
    };
  }

  String get _emptyHeading => switch (widget.type) {
    _TabType.bookmarks => 'Пока ничего не сохранено',
    _TabType.likes => 'Пока ничего не отмечено',
    _TabType.dislikes => 'Пока ничего не скрыто',
  };

  String get _emptySubtext => switch (widget.type) {
    _TabType.bookmarks => 'Сохраняйте статьи, чтобы вернуться к ним позже',
    _TabType.likes => 'Отмечайте статьи, которые вам понравились',
    _TabType.dislikes => 'Скрытые статьи не будут появляться в ленте',
  };

  IconData get _emptyIcon => switch (widget.type) {
    _TabType.bookmarks => Icons.bookmark_outline_rounded,
    _TabType.likes => Icons.thumb_up_outlined,
    _TabType.dislikes => Icons.thumb_down_outlined,
  };

  @override
  Widget build(BuildContext context) {
    final stateAsync = _watchState();

    return stateAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.error_outline,
              color: AppColors.textHint,
              size: 40,
            ),
            const SizedBox(height: 12),
            TextButton(
              onPressed: () {
                switch (widget.type) {
                  case _TabType.bookmarks:
                    ref.invalidate(bookmarksNotifierProvider);
                  case _TabType.likes:
                    ref.invalidate(likesNotifierProvider);
                  case _TabType.dislikes:
                    ref.invalidate(dislikesNotifierProvider);
                }
              },
              child: const Text('Повторить'),
            ),
          ],
        ),
      ),
      data: (tabState) {
        if (tabState.items.isEmpty && !tabState.isRefreshing) {
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Illustration placeholder
                Container(
                  width: 96,
                  height: 96,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: const Color(0xFFECE7FF), // accent-violet.bg-soft
                  ),
                  child: Icon(
                    _emptyIcon,
                    size: 44,
                    color: const Color(0xFF7C6CFF), // accent-violet.fg
                  ),
                ),
                const SizedBox(height: 24),
                Text(
                  _emptyHeading,
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF0E1525), // text.primary = neutral-900
                  ),
                ),
                const SizedBox(height: 8),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 280),
                  child: Text(
                    _emptySubtext,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 14,
                      fontWeight: FontWeight.w400,
                      color: Color(0xFF8792A6), // text.tertiary = neutral-400
                    ),
                  ),
                ),
              ],
            ),
          );
        }

        return RefreshIndicator(
          onRefresh: _refresh,
          child: ListView.builder(
            controller: _scrollController,
            padding: EdgeInsets.zero,
            itemCount: tabState.items.length + 1,
            itemBuilder: (context, index) {
              if (index < tabState.items.length) {
                return RepaintBoundary(
                  child: ContentCardWidget(item: tabState.items[index]),
                );
              }
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Center(
                  child: tabState.isLoadingMore
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const SizedBox.shrink(),
                ),
              );
            },
          ),
        );
      },
    );
  }
}
