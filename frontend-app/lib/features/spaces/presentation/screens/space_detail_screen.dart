import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/sources/domain/space_color.dart';
import '../../../../shared/widgets/icon_tile.dart';
import '../../../../shared/widgets/profile_list_item.dart';
import '../../../../shared/widgets/segmented_control.dart';
import '../../../../theme/app_tokens.dart';
import '../../../../theme/colors.dart';
import '../../../../theme/radii.dart';
import '../../../../theme/shadows.dart';
import '../../../../ui/cards/card_item.dart';
import '../../../../ui/cards/content_item_card_mapper.dart';
import '../../../../ui/cards/long_card.dart';
import '../../../../ui/cards/short_card.dart';
import '../../../../widgets/destructive_button.dart';
import '../../../feed/presentation/providers/article_actions_provider.dart';
import '../providers/spaces_providers.dart';

class SpaceDetailScreen extends ConsumerStatefulWidget {
  const SpaceDetailScreen({super.key, required this.spaceId});

  final String spaceId;

  @override
  ConsumerState<SpaceDetailScreen> createState() =>
      _SpaceDetailScreenState();
}

class _SpaceDetailScreenState extends ConsumerState<SpaceDetailScreen> {
  int _selectedTab = 0;
  late final ScrollController _feedScroll;

  @override
  void initState() {
    super.initState();
    _feedScroll = ScrollController()..addListener(_onScroll);
  }

  @override
  void dispose() {
    _feedScroll
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    final pos = _feedScroll.position;
    if (pos.maxScrollExtent > 0 &&
        pos.pixels >= pos.maxScrollExtent * 0.8) {
      ref
          .read(spaceFeedNotifierProvider(widget.spaceId).notifier)
          .loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final space = ref.watch(spaceByIdProvider(widget.spaceId));
    final Color dotColor = space?.color.accent.fg ?? NFColors.accent;

    return Material(
      type: MaterialType.canvas,
      color: NFColors.bg,
      child: SafeArea(
        bottom: false,
        child: Column(
          children: <Widget>[
            _SpaceHeader(
              title: space?.name ?? 'Пространство',
              dotColor: dotColor,
              onEdit: () =>
                  context.push('/spaces/${widget.spaceId}/edit'),
              onBack: () => context.pop(),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 6, 18, 14),
              child: SegmentedControl(
                labels: const <String>['Лента', 'Настройки'],
                selectedIndex: _selectedTab,
                onChanged: (int i) => setState(() => _selectedTab = i),
              ),
            ),
            Expanded(
              child: IndexedStack(
                index: _selectedTab,
                children: <Widget>[
                  _FeedTab(spaceId: widget.spaceId, scroll: _feedScroll),
                  _SettingsTab(spaceId: widget.spaceId),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SpaceHeader extends StatelessWidget {
  const _SpaceHeader({
    required this.title,
    required this.dotColor,
    required this.onBack,
    required this.onEdit,
  });

  final String title;
  final Color dotColor;
  final VoidCallback onBack;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
      child: Row(
        children: <Widget>[
          _HeaderIconBtn(
            icon: Icons.arrow_back,
            onTap: onBack,
            semanticLabel: 'Назад',
          ),
          const SizedBox(width: 8),
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: dotColor,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 22,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.4,
                color: NFColors.ink,
                height: 1.1,
              ),
            ),
          ),
          _HeaderIconBtn(
            icon: Icons.edit_outlined,
            onTap: onEdit,
            semanticLabel: 'Редактировать',
          ),
        ],
      ),
    );
  }
}

class _HeaderIconBtn extends StatelessWidget {
  const _HeaderIconBtn({
    required this.icon,
    required this.onTap,
    required this.semanticLabel,
  });

  final IconData icon;
  final VoidCallback onTap;
  final String semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: semanticLabel,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: NFColors.surface,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: NFColors.hairline),
            boxShadow: NFShadows.card,
          ),
          alignment: Alignment.center,
          child: Icon(icon, size: 18, color: NFColors.ink),
        ),
      ),
    );
  }
}

class _FeedTab extends ConsumerWidget {
  const _FeedTab({required this.spaceId, required this.scroll});

  final String spaceId;
  final ScrollController scroll;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feedAsync = ref.watch(spaceFeedNotifierProvider(spaceId));
    return feedAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (Object e, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            'Не удалось загрузить ленту: $e',
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontFamily: 'Nunito',
              color: NFColors.mute,
              fontSize: 14,
            ),
          ),
        ),
      ),
      data: (page) {
        if (page.items.isEmpty) {
          return const Center(
            child: Padding(
              padding: EdgeInsets.all(32),
              child: Text(
                'В этом пространстве пока нет публикаций',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontFamily: 'Nunito',
                  color: NFColors.mute,
                  fontSize: 14,
                ),
              ),
            ),
          );
        }
        return RefreshIndicator(
          onRefresh: () => ref
              .read(spaceFeedNotifierProvider(spaceId).notifier)
              .refresh(),
          child: ListView.separated(
            controller: scroll,
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 32),
            itemCount: page.items.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (BuildContext context, int i) {
              final item = page.items[i];
              final CardItem cardItem = contentItemToCardItem(item);
              final bool isLong = isLongFormContent(item);
              return _SpaceFeedCard(
                articleId: item.id,
                cardItem: cardItem,
                isLong: isLong,
              );
            },
          ),
        );
      },
    );
  }
}

class _SpaceFeedCard extends ConsumerWidget {
  const _SpaceFeedCard({
    required this.articleId,
    required this.cardItem,
    required this.isLong,
  });

  final String articleId;
  final CardItem cardItem;
  final bool isLong;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ArticleActionState state = ref.watch(
      articleActionsNotifierProvider.select(
        (Map<String, ArticleActionState> map) =>
            map[articleId] ?? const ArticleActionState(),
      ),
    );

    void handleOpen(CardItem item) =>
        context.push('/article/${item.id}');
    void handleReact(CardItem item, ReactionKind kind) {
      final notifier =
          ref.read(articleActionsNotifierProvider.notifier);
      switch (kind) {
        case ReactionKind.like:
          notifier.like(item.id);
        case ReactionKind.dislike:
          notifier.dislike(item.id);
        case ReactionKind.bookmark:
          notifier.toggleSave(item.id);
      }
    }

    return RepaintBoundary(
      child: isLong
          ? LongCard(
              item: cardItem,
              onOpen: handleOpen,
              onReact: handleReact,
              isLiked: state.isLiked,
              isDisliked: state.isDisliked,
              isBookmarked: state.isSaved,
            )
          : ShortCard(
              item: cardItem,
              onOpen: handleOpen,
              onReact: handleReact,
              isLiked: state.isLiked,
              isDisliked: state.isDisliked,
              isBookmarked: state.isSaved,
            ),
    );
  }
}

class _SettingsTab extends ConsumerWidget {
  const _SettingsTab({required this.spaceId});

  final String spaceId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final space = ref.watch(spaceByIdProvider(spaceId));
    if (space == null) {
      return const Center(child: CircularProgressIndicator());
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(14, 0, 14, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Container(
            decoration: BoxDecoration(
              color: NFColors.surface,
              borderRadius: NFRadii.brLg,
              border: Border.all(color: NFColors.hairline),
              boxShadow: NFShadows.card,
            ),
            child: Column(
              children: <Widget>[
                ProfileListItem(
                  iconVariant: AccentVariant.sky,
                  icon: Icons.edit_outlined,
                  title: 'Название',
                  subtitle: space.name,
                  onTap: () => context.push('/spaces/${space.id}/edit'),
                ),
                const _Hairline(),
                ProfileListItem(
                  iconVariant: AccentVariant.amber,
                  icon: Icons.palette_outlined,
                  title: 'Цвет',
                  trailing: _ColourSwatch(color: space.color),
                  onTap: () => context.push('/spaces/${space.id}/edit'),
                ),
                const _Hairline(),
                ProfileListItem(
                  iconVariant: AccentVariant.mint,
                  icon: Icons.folder_outlined,
                  title: 'Источники',
                  subtitle: '${space.sourceCount} источн.',
                  onTap: () => context.push('/spaces/${space.id}/edit'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          DestructiveButton(
            label: 'Удалить пространство',
            icon: Icons.delete_outline,
            onPressed: () =>
                _confirmDelete(context, ref, space.id, space.name, tokens),
          ),
          SizedBox(height: 24 + MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }

  void _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    String id,
    String name,
    AppTokens tokens,
  ) {
    showDialog<void>(
      context: context,
      builder: (BuildContext ctx) => AlertDialog(
        backgroundColor: NFColors.surface,
        shape: RoundedRectangleBorder(borderRadius: NFRadii.brLg),
        elevation: 4,
        title: Text(
          'Удалить «$name»?',
          style: const TextStyle(
            fontFamily: 'Nunito',
            fontSize: 18,
            fontWeight: FontWeight.w700,
            color: NFColors.ink,
          ),
        ),
        content: const Text(
          'Источники останутся в каталоге. Пространство будет удалено.',
          style: TextStyle(
            fontFamily: 'Nunito',
            fontSize: 14,
            color: NFColors.mute,
            height: 1.4,
          ),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text(
              'Отмена',
              style: TextStyle(
                fontFamily: 'Nunito',
                color: NFColors.mute,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              final messenger = ScaffoldMessenger.of(context);
              final router = GoRouter.of(context);
              try {
                await ref
                    .read(spacesListNotifierProvider.notifier)
                    .deleteSpace(id);
                router.pop();
              } catch (e) {
                messenger.showSnackBar(
                  SnackBar(content: Text('Не удалось: $e')),
                );
              }
            },
            child: Text(
              'Удалить',
              style: TextStyle(
                fontFamily: 'Nunito',
                color: tokens.colors.status.error,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Hairline extends StatelessWidget {
  const _Hairline();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(left: 72),
      child: Divider(height: 1, thickness: 1, color: NFColors.hairline),
    );
  }
}

class _ColourSwatch extends StatelessWidget {
  const _ColourSwatch({required this.color});

  final SpaceColor color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 22,
      height: 22,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: color.accent.fg,
        boxShadow: NFShadows.card,
      ),
    );
  }
}
