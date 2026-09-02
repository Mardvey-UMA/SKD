import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/collections/presentation/providers/collections_provider.dart';
import '../../features/feed/domain/models/content_item.dart';
import '../../features/feed/presentation/providers/article_actions_provider.dart';
import '../../features/interactions/domain/models/interaction_event.dart';
import '../../features/interactions/presentation/providers/interaction_service_provider.dart';
import '../../theme/colors.dart';
import '../../ui/atoms/empty_state.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';
import '../../ui/atoms/stripe_placeholder.dart';
import '../../ui/cards/card_item.dart';
import '../../ui/cards/long_card.dart';
import '../../ui/cards/short_card.dart';

/// Kind of user-curated collection rendered by [BookmarksScreen].
///
/// Mirrors the `kind` prop in `design/reference/mockup/screens.jsx` →
/// `BookmarksScreen` — a single component that flips its title, kicker,
/// empty-state copy, and source provider based on this enum.
enum BookmarksKind { bookmark, like, dislike }

/// Single screen showing one of three system collections — saved articles,
/// liked articles, or hidden (disliked) articles — selected via [kind].
///
/// Mirrors the `BookmarksScreen` JSX prototype in
/// `design/reference/mockup/screens.jsx`: a rounded back-button pill +
/// kicker + 28-px kind-specific title, then either an [EmptyState] with
/// the kind-specific icon/copy, or a vertical stack of [ShortCard] /
/// [LongCard] items.
///
/// Data comes from the existing `features/collections` providers —
/// [bookmarksNotifierProvider] / [likesNotifierProvider] /
/// [dislikesNotifierProvider] — so pagination, refresh, and paging cursors
/// continue to work exactly as they did in the pre-redesign tabbed screen.
/// Ad-injection is intentionally not applied here (per P19 spec).
class BookmarksScreen extends ConsumerStatefulWidget {
  const BookmarksScreen({super.key, required this.kind});

  final BookmarksKind kind;

  @override
  ConsumerState<BookmarksScreen> createState() => _BookmarksScreenState();
}

class _BookmarksScreenState extends ConsumerState<BookmarksScreen> {
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
    final ScrollPosition pos = _scrollController.position;
    if (pos.maxScrollExtent > 0 &&
        pos.pixels >= pos.maxScrollExtent - 240) {
      _loadMore();
    }
  }

  void _loadMore() {
    switch (widget.kind) {
      case BookmarksKind.bookmark:
        ref.read(bookmarksNotifierProvider.notifier).loadMore();
      case BookmarksKind.like:
        ref.read(likesNotifierProvider.notifier).loadMore();
      case BookmarksKind.dislike:
        ref.read(dislikesNotifierProvider.notifier).loadMore();
    }
  }

  Future<void> _refresh() async {
    switch (widget.kind) {
      case BookmarksKind.bookmark:
        await ref.read(bookmarksNotifierProvider.notifier).refresh();
      case BookmarksKind.like:
        await ref.read(likesNotifierProvider.notifier).refresh();
      case BookmarksKind.dislike:
        await ref.read(dislikesNotifierProvider.notifier).refresh();
    }
  }

  AsyncValue<CollectionTabState> _watchState() {
    return switch (widget.kind) {
      BookmarksKind.bookmark => ref.watch(bookmarksNotifierProvider),
      BookmarksKind.like => ref.watch(likesNotifierProvider),
      BookmarksKind.dislike => ref.watch(dislikesNotifierProvider),
    };
  }

  _KindMeta get _meta => _KindMeta.forKind(widget.kind);

  void _handleBack() {
    if (GoRouter.of(context).canPop()) {
      context.pop();
    } else {
      context.go('/shell/collections');
    }
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<CollectionTabState> stateAsync = _watchState();

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
          onRefresh: _refresh,
          child: stateAsync.when(
            loading: () => _ScaffoldShell(
              meta: _meta,
              count: 0,
              onBack: _handleBack,
              scrollController: _scrollController,
              child: const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 48),
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
            ),
            error: (Object e, _) => _ScaffoldShell(
              meta: _meta,
              count: 0,
              onBack: _handleBack,
              scrollController: _scrollController,
              child: SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 32,
                  ),
                  child: Center(
                    child: TextButton(
                      onPressed: _refresh,
                      child: const Text('Повторить'),
                    ),
                  ),
                ),
              ),
            ),
            data: (CollectionTabState tab) {
              final List<ContentItem> items = tab.items;
              return _ScaffoldShell(
                meta: _meta,
                count: items.length,
                onBack: _handleBack,
                scrollController: _scrollController,
                child: items.isEmpty
                    ? SliverToBoxAdapter(
                        child: Padding(
                          padding:
                              const EdgeInsets.fromLTRB(14, 8, 14, 120),
                          child: EmptyState(
                            iconName: _meta.icon,
                            accent: _meta.accent,
                            title: _meta.emptyTitle,
                            desc: _meta.emptyDesc,
                          ),
                        ),
                      )
                    : SliverPadding(
                        padding:
                            const EdgeInsets.fromLTRB(14, 4, 14, 120),
                        sliver: SliverList.separated(
                          itemCount: items.length + 1,
                          separatorBuilder: (_, int i) => i < items.length - 1
                              ? const SizedBox(height: 14)
                              : const SizedBox.shrink(),
                          itemBuilder: (BuildContext context, int index) {
                            if (index == items.length) {
                              return _LoadMoreFooter(
                                isLoadingMore: tab.isLoadingMore,
                                hasNext: tab.hasNext,
                              );
                            }
                            final ContentItem item = items[index];
                            return RepaintBoundary(
                              key: ValueKey<String>('bm-${item.id}'),
                              child: _BookmarkItemView(item: item),
                            );
                          },
                        ),
                      ),
              );
            },
          ),
        ),
      ),
    );
  }
}

class _ScaffoldShell extends StatelessWidget {
  const _ScaffoldShell({
    required this.meta,
    required this.count,
    required this.onBack,
    required this.scrollController,
    required this.child,
  });

  final _KindMeta meta;
  final int count;
  final VoidCallback onBack;
  final ScrollController scrollController;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      controller: scrollController,
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: <Widget>[
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
            child: _Header(
              meta: meta,
              count: count,
              onBack: onBack,
            ),
          ),
        ),
        child,
      ],
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.meta, required this.count, required this.onBack});

  final _KindMeta meta;
  final int count;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: <Widget>[
        Semantics(
          label: 'Назад',
          button: true,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onBack,
            child: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: NFColors.surface,
                shape: BoxShape.circle,
                border: Border.all(color: NFColors.hairline),
              ),
              alignment: Alignment.center,
              child: const NFIcon('back', size: 18, color: NFColors.ink),
            ),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              NFText.mono('${meta.kicker}: $count'),
              const SizedBox(height: 2),
              Text(
                meta.title,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 28,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.9,
                  height: 1.05,
                  color: NFColors.ink,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _LoadMoreFooter extends StatelessWidget {
  const _LoadMoreFooter({required this.isLoadingMore, required this.hasNext});

  final bool isLoadingMore;
  final bool hasNext;

  @override
  Widget build(BuildContext context) {
    if (isLoadingMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: Center(
          child: SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
    }
    return hasNext ? const SizedBox(height: 8) : const SizedBox(height: 4);
  }
}

class _BookmarkItemView extends ConsumerWidget {
  const _BookmarkItemView({required this.item});

  final ContentItem item;

  String? _deviceType() {
    if (kIsWeb) return 'web';
    if (Platform.isAndroid) return 'android';
    return null;
  }

  void _onOpen(BuildContext context, WidgetRef ref) {
    ref.read(interactionServiceProvider).trackEvent(
          InteractionEvent(
            contentId: item.id,
            action: InteractionAction.open,
            timestamp: DateTime.now(),
            deviceType: _deviceType(),
          ),
        );
    context.push('/shell/feed/${item.id}');
  }

  void _onReact(WidgetRef ref, ReactionKind kind) {
    final notifier = ref.read(articleActionsNotifierProvider.notifier);
    final InteractionAction action;
    switch (kind) {
      case ReactionKind.like:
        notifier.like(item.id);
        action = InteractionAction.like;
      case ReactionKind.dislike:
        notifier.dislike(item.id);
        action = InteractionAction.dislike;
      case ReactionKind.bookmark:
        notifier.toggleSave(item.id);
        action = InteractionAction.bookmark;
    }
    final String? appVersion =
        ref.read(packageInfoProvider).valueOrNull?.version;
    ref.read(interactionServiceProvider).trackEvent(
          InteractionEvent(
            contentId: item.id,
            action: action,
            timestamp: DateTime.now(),
            deviceType: _deviceType(),
            appVersion: appVersion,
          ),
        );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ArticleActionState action = ref.watch(
      articleActionsNotifierProvider.select(
        (Map<String, ArticleActionState> map) =>
            map[item.id] ?? const ArticleActionState(),
      ),
    );
    final CardItem card = _toCardItem(item);
    final bool isLong = _isLongForm(item);

    if (isLong) {
      return LongCard(
        item: card,
        onOpen: (_) => _onOpen(context, ref),
        onReact: (_, ReactionKind k) => _onReact(ref, k),
        isLiked: action.isLiked,
        isDisliked: action.isDisliked,
        isBookmarked: action.isSaved,
      );
    }
    return ShortCard(
      item: card,
      onOpen: (_) => _onOpen(context, ref),
      onReact: (_, ReactionKind k) => _onReact(ref, k),
      isLiked: action.isLiked,
      isDisliked: action.isDisliked,
      isBookmarked: action.isSaved,
    );
  }
}

bool _isLongForm(ContentItem item) {
  final int? minutes = item.metadata?.readTimeMinutes;
  if (minutes != null && minutes >= 8) return true;
  final String? preview = item.bestPreview;
  return preview != null && preview.length >= 220;
}

CardItem _toCardItem(ContentItem item) {
  final CardImages images = item.hasMedia
      ? (item.media.length > 1 ? CardImages.multi : CardImages.one)
      : CardImages.none;
  const List<StripeTone> palette = <StripeTone>[
    StripeTone.ink,
    StripeTone.accent,
    StripeTone.lime,
    StripeTone.rose,
    StripeTone.violet,
    StripeTone.teal,
  ];
  final StripeTone tone = palette[item.id.hashCode.abs() % palette.length];
  final int seed = item.id.hashCode & 0x7fffffff;
  return CardItem(
    id: item.id,
    source: item.sourceName?.isNotEmpty == true
        ? item.sourceName!
        : item.authorName ?? 'Источник',
    time: item.publishedAt ?? DateTime.now(),
    readTime: item.metadata?.readTimeMinutes != null
        ? '${item.metadata!.readTimeMinutes} мин чтения'
        : null,
    title: item.displayTitle,
    snippet: item.bestPreview ?? '',
    images: images,
    tone: tone,
    seed: seed,
  );
}

class _KindMeta {
  const _KindMeta({
    required this.title,
    required this.kicker,
    required this.emptyTitle,
    required this.emptyDesc,
    required this.icon,
    required this.accent,
  });

  final String title;
  final String kicker;
  final String emptyTitle;
  final String emptyDesc;
  final String icon;
  final EmptyStateAccent accent;

  static _KindMeta forKind(BookmarksKind kind) {
    switch (kind) {
      case BookmarksKind.bookmark:
        return const _KindMeta(
          title: 'Сохранённое',
          kicker: 'СОХРАНЕНО',
          emptyTitle: 'Пусто',
          emptyDesc: 'Сохранённое покажется здесь',
          icon: 'bookmark',
          accent: EmptyStateAccent.lime,
        );
      case BookmarksKind.like:
        return const _KindMeta(
          title: 'Понравилось',
          kicker: 'ОДОБРЕНО',
          emptyTitle: 'Пусто',
          emptyDesc: 'Понравившееся покажется здесь',
          icon: 'thumb-up',
          accent: EmptyStateAccent.accent,
        );
      case BookmarksKind.dislike:
        return const _KindMeta(
          title: 'Не понравилось',
          kicker: 'СКРЫТО',
          emptyTitle: 'Пусто',
          emptyDesc: 'Скрытое покажется здесь',
          icon: 'thumb-down',
          accent: EmptyStateAccent.ink,
        );
    }
  }
}
