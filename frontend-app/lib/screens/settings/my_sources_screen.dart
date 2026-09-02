import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/feature_flags.dart';
import '../../core/sources/domain/source_type.dart';
import '../../features/my_additions/domain/entities/source_addition.dart';
import '../../features/my_additions/presentation/providers/my_additions_provider.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/icon_btn.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';

/// «Мои добавленные» — redesigned screen.
///
/// Mirrors `MySourcesScreen` in `design/reference/mockup/screens.jsx`:
/// back + title with count, search input, and either an empty-state card
/// with an «Добавить источник» CTA, or a list of the user's added
/// sources. Uses the existing [myAdditionsNotifierProvider] so the real
/// GET `/api/feed/my-additions` API drives the list — no mock data.
///
/// The trash icon per row is rendered as in the JSX but the delete API
/// is not yet exposed by the backend — tapping it shows a snackbar.
/// When the endpoint lands, wire it through the existing repository.
class MySourcesScreen extends ConsumerStatefulWidget {
  const MySourcesScreen({super.key});

  @override
  ConsumerState<MySourcesScreen> createState() => _MySourcesScreenState();
}

class _MySourcesScreenState extends ConsumerState<MySourcesScreen> {
  String _query = '';
  late final TextEditingController _searchController;
  late final ScrollController _scroll;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _scroll = ScrollController()..addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scroll
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    final pos = _scroll.position;
    if (pos.maxScrollExtent > 0 && pos.pixels >= pos.maxScrollExtent * 0.8) {
      ref.read(myAdditionsNotifierProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(myAdditionsNotifierProvider);
    final int total = state.valueOrNull?.items.length ?? 0;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: state.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(
                    e.toString(),
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 14,
                      color: NFColors.mute,
                    ),
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
          ),
          data: (value) => _Body(
            items: value.items,
            total: total,
            query: _query,
            searchController: _searchController,
            scroll: _scroll,
            onQueryChanged: (v) => setState(() => _query = v),
          ),
        ),
      ),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body({
    required this.items,
    required this.total,
    required this.query,
    required this.searchController,
    required this.scroll,
    required this.onQueryChanged,
  });

  final List<SourceAddition> items;
  final int total;
  final String query;
  final TextEditingController searchController;
  final ScrollController scroll;
  final ValueChanged<String> onQueryChanged;

  @override
  Widget build(BuildContext context) {
    final List<SourceAddition> filtered = query.trim().isEmpty
        ? items
        : items
            .where(
              (a) => a.name.toLowerCase().contains(query.toLowerCase()),
            )
            .toList();

    return CustomScrollView(
      controller: scroll,
      slivers: <Widget>[
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
            child: Row(
              children: <Widget>[
                IconBtn(
                  iconName: 'back',
                  onTap: () {
                    if (context.canPop()) {
                      context.pop();
                    } else {
                      context.go('/sources');
                    }
                  },
                  semanticLabel: 'Назад',
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Мои добавленные ($total из ${FeatureFlags.maxAddedSources})',
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.3,
                      color: NFColors.ink,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 10, 14, 0),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: NFColors.surface,
                borderRadius: BorderRadius.circular(999),
                border: Border.all(color: NFColors.hairline),
              ),
              child: Row(
                children: <Widget>[
                  const NFIcon('search', size: 16, color: NFColors.mute),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: searchController,
                      onChanged: onQueryChanged,
                      style: const TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 14,
                        color: NFColors.ink,
                      ),
                      decoration: const InputDecoration(
                        isDense: true,
                        border: InputBorder.none,
                        hintText: 'Поиск по названию',
                        hintStyle: TextStyle(
                          fontFamily: 'Nunito',
                          fontSize: 14,
                          color: NFColors.mute,
                        ),
                        contentPadding: EdgeInsets.symmetric(vertical: 12),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        if (items.isEmpty)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 40, 14, 0),
              child: _EmptyCard(onAdd: () => context.push('/sources/add')),
            ),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 130),
            sliver: SliverList.separated(
              itemCount: filtered.length,
              separatorBuilder: (_, _) => const SizedBox(height: 6),
              itemBuilder: (context, i) => _Item(addition: filtered[i]),
            ),
          ),
      ],
    );
  }
}

class _Item extends StatelessWidget {
  const _Item({required this.addition});

  final SourceAddition addition;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: NFColors.hairline),
      ),
      child: Row(
        children: <Widget>[
          _PlatformGlyph(type: addition.type),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  addition.name.isEmpty ? addition.sourceId : addition.name,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: NFColors.ink,
                  ),
                ),
                const SizedBox(height: 2),
                NFText.mono(addition.sourceId),
              ],
            ),
          ),
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Удаление пока недоступно'),
                ),
              );
            },
            child: Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: NFColors.hairline),
              ),
              alignment: Alignment.center,
              child: const NFIcon('trash', size: 15, color: NFColors.warn),
            ),
          ),
        ],
      ),
    );
  }
}

class _PlatformGlyph extends StatelessWidget {
  const _PlatformGlyph({required this.type});

  final SourceType type;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 28,
      height: 28,
      decoration: BoxDecoration(
        color: NFColors.chipBg,
        borderRadius: BorderRadius.circular(8),
      ),
      alignment: Alignment.center,
      child: Text(
        type.shortLabel,
        style: const TextStyle(
          fontFamily: 'Nunito',
          fontSize: 10,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.4,
          color: NFColors.ink,
        ),
      ),
    );
  }
}

class _EmptyCard extends StatelessWidget {
  const _EmptyCard({required this.onAdd});

  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 40, 20, 40),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.brLg,
        border: Border.all(color: NFColors.hairline),
      ),
      child: Column(
        children: <Widget>[
          const NFIcon('bookmark', size: 32, color: NFColors.mute2),
          const SizedBox(height: 10),
          const Text(
            'Вы ещё не добавляли источники',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: NFColors.ink,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            'Перейдите в Каталог и добавьте свой первый источник.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 13.5,
              height: 1.5,
              color: NFColors.mute,
            ),
          ),
          const SizedBox(height: 16),
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onAdd,
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: NFColors.ink,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: const <Widget>[
                  NFIcon('plus', size: 14, color: Colors.white),
                  SizedBox(width: 6),
                  Text(
                    'Добавить источник',
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
