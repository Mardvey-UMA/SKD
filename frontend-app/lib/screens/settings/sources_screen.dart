import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/sources/domain/source_type.dart';
import '../../features/blocked_sources/domain/entities/blocked_source.dart';
import '../../features/blocked_sources/presentation/providers/blocked_sources_provider.dart';
import '../../features/sources_catalog/domain/entities/source.dart';
import '../../features/sources_catalog/domain/value_objects/catalog_query.dart';
import '../../features/sources_catalog/presentation/providers/sources_catalog_provider.dart';
import '../../features/subscription/presentation/providers/subscription_provider.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/icon_btn.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';
import '../../ui/gates/plan_gate.dart';

/// Sources catalog — redesigned screen.
///
/// Mirrors `SourcesScreen` in `design/reference/mockup/screens.jsx`:
/// back + title + «+» button (with inline «PREMIUM» badge for free users),
/// large «Каталог источников.» display, Каталог/Скрытые segmented toggle,
/// search input, platform filter chips, «Мои добавленные» entry row (only
/// in catalog mode), and the list.
///
/// All premium-gating goes through [PlanGate] — this screen never checks
/// `isPremium` itself for navigation decisions. The inline «PREMIUM» label
/// inside the «+» button is purely visual and derived from the same
/// subscription provider.
class SourcesScreen extends ConsumerStatefulWidget {
  const SourcesScreen({super.key, this.highlightId});

  final String? highlightId;

  @override
  ConsumerState<SourcesScreen> createState() => _SourcesScreenState();
}

enum _Mode { catalog, hidden }

class _SourcesScreenState extends ConsumerState<SourcesScreen> {
  _Mode _mode = _Mode.catalog;
  SourceType? _platform;
  String _query = '';
  late final ScrollController _scroll;
  late final TextEditingController _searchController;

  @override
  void initState() {
    super.initState();
    _scroll = ScrollController()..addListener(_onScroll);
    _searchController = TextEditingController();
  }

  @override
  void dispose() {
    _scroll
      ..removeListener(_onScroll)
      ..dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_mode != _Mode.catalog) return;
    final pos = _scroll.position;
    if (pos.maxScrollExtent > 0 && pos.pixels >= pos.maxScrollExtent * 0.8) {
      ref
          .read(sourcesCatalogNotifierProvider(
            CatalogQuery(type: _platform, q: _query),
          ).notifier)
          .loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final int hiddenCount =
        ref.watch(blockedSourcesNotifierProvider).valueOrNull?.length ?? 0;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: CustomScrollView(
          controller: _scroll,
          slivers: <Widget>[
            const SliverToBoxAdapter(child: _Header()),
            SliverToBoxAdapter(child: _Display(mode: _mode)),
            SliverToBoxAdapter(
              child: _ModeToggle(
                mode: _mode,
                hiddenCount: hiddenCount,
                onChanged: (m) => setState(() {
                  _mode = m;
                  _platform = null;
                  _query = '';
                  _searchController.clear();
                }),
              ),
            ),
            SliverToBoxAdapter(
              child: _SearchInput(
                controller: _searchController,
                onChanged: (v) => setState(() => _query = v),
              ),
            ),
            SliverToBoxAdapter(
              child: _PlatformChips(
                selected: _platform,
                onChanged: (p) => setState(() => _platform = p),
              ),
            ),
            if (_mode == _Mode.catalog)
              const SliverToBoxAdapter(child: _MyAdditionsEntry()),
            if (_mode == _Mode.catalog)
              _CatalogList(
                query: CatalogQuery(type: _platform, q: _query),
                highlightId: widget.highlightId,
              )
            else
              _HiddenList(platform: _platform, query: _query),
            const SliverToBoxAdapter(child: SizedBox(height: 130)),
          ],
        ),
      ),
    );
  }
}

class _Header extends ConsumerWidget {
  const _Header();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bool isPremium =
        ref.watch(subscriptionNotifierProvider).valueOrNull?.isPremium ?? false;
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Row(
        children: <Widget>[
          IconBtn(
            iconName: 'back',
            onTap: () {
              if (context.canPop()) {
                context.pop();
              } else {
                context.go('/shell/settings');
              }
            },
            semanticLabel: 'Назад',
          ),
          const SizedBox(width: 10),
          const Expanded(
            child: Text(
              'Источники',
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 17,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.3,
                color: NFColors.ink,
              ),
            ),
          ),
          PlanGate(
            onTap: () => context.push('/sources/add'),
            child: Container(
              height: 38,
              padding: EdgeInsets.symmetric(horizontal: isPremium ? 0 : 12),
              constraints: const BoxConstraints(minWidth: 38),
              decoration: BoxDecoration(
                color: NFColors.ink,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  const NFIcon('plus', size: 18, color: Colors.white),
                  if (!isPremium) ...<Widget>[
                    const SizedBox(width: 6),
                    const Text(
                      'PREMIUM',
                      style: TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.4,
                        color: NFColors.lime,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Display extends StatelessWidget {
  const _Display({required this.mode});

  final _Mode mode;

  @override
  Widget build(BuildContext context) {
    final String line1 = mode == _Mode.hidden ? 'Скрытые' : 'Каталог';
    final String line2 = mode == _Mode.hidden ? 'источники.' : 'источников.';
    final String desc = mode == _Mode.hidden
        ? 'Материалы этих источников не попадают в ленту. Верните те, что снова хотите читать.'
        : 'Найдите Telegram-каналы, хабы Habr или разделы VC.RU — или добавьте свой источник.';
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 4, 18, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            '$line1\n$line2',
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 30,
              height: 1.05,
              letterSpacing: -1.0,
              fontWeight: FontWeight.w700,
              color: NFColors.ink,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            desc,
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 14,
              color: NFColors.mute,
            ),
          ),
        ],
      ),
    );
  }
}

class _ModeToggle extends StatelessWidget {
  const _ModeToggle({
    required this.mode,
    required this.hiddenCount,
    required this.onChanged,
  });

  final _Mode mode;
  final int hiddenCount;
  final ValueChanged<_Mode> onChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 0, 14, 2),
      child: Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: NFColors.hairline),
        ),
        child: Row(
          children: <Widget>[
            Expanded(child: _tab(_Mode.catalog, 'Каталог')),
            Expanded(
              child: _tab(
                _Mode.hidden,
                hiddenCount > 0 ? 'Скрытые · $hiddenCount' : 'Скрытые',
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _tab(_Mode m, String label) {
    final bool on = mode == m;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => onChanged(m),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
        decoration: BoxDecoration(
          color: on ? NFColors.ink : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontFamily: 'Nunito',
            fontSize: 13.5,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.1,
            color: on ? Colors.white : NFColors.ink2,
          ),
        ),
      ),
    );
  }
}

class _SearchInput extends StatelessWidget {
  const _SearchInput({required this.controller, required this.onChanged});

  final TextEditingController controller;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 6, 14, 0),
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
                controller: controller,
                onChanged: onChanged,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 14,
                  color: NFColors.ink,
                ),
                decoration: const InputDecoration(
                  isDense: true,
                  border: InputBorder.none,
                  hintText: 'Поиск по названию или ссылке',
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
    );
  }
}

class _PlatformChips extends StatelessWidget {
  const _PlatformChips({required this.selected, required this.onChanged});

  final SourceType? selected;
  final ValueChanged<SourceType?> onChanged;

  @override
  Widget build(BuildContext context) {
    const List<_ChipSpec> chips = <_ChipSpec>[
      _ChipSpec(label: 'Все', value: null),
      _ChipSpec(label: 'Telegram', value: SourceType.telegram),
      _ChipSpec(label: 'Habr', value: SourceType.habr),
      _ChipSpec(label: 'VC.RU', value: SourceType.vcRu),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 0),
      child: Wrap(
        spacing: 6,
        runSpacing: 6,
        children: <Widget>[
          for (final chip in chips)
            _chip(chip.label, chip.value, chip.value == selected),
        ],
      ),
    );
  }

  Widget _chip(String label, SourceType? value, bool on) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => onChanged(value),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: on ? NFColors.accent : NFColors.surface,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: on ? NFColors.accent : NFColors.hairline,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontFamily: 'Nunito',
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: on ? Colors.white : NFColors.ink2,
          ),
        ),
      ),
    );
  }
}

class _ChipSpec {
  const _ChipSpec({required this.label, required this.value});
  final String label;
  final SourceType? value;
}

class _MyAdditionsEntry extends StatelessWidget {
  const _MyAdditionsEntry();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 0),
      child: PlanGate(
        onTap: () => context.push('/my-additions'),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: NFColors.surface,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: NFColors.hairline),
          ),
          child: Row(
            children: <Widget>[
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: NFColors.lime,
                  borderRadius: BorderRadius.circular(10),
                ),
                alignment: Alignment.center,
                child: const NFIcon('star', size: 18, color: NFColors.limeInk),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const <Widget>[
                    Text(
                      'Мои добавленные',
                      style: TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: NFColors.ink,
                      ),
                    ),
                    SizedBox(height: 2),
                    NFText.mono('СПИСОК · МОЖНО УДАЛЯТЬ'),
                  ],
                ),
              ),
              const NFIcon('chevron', size: 16, color: NFColors.mute),
            ],
          ),
        ),
      ),
    );
  }
}

class _CatalogList extends ConsumerWidget {
  const _CatalogList({required this.query, required this.highlightId});

  final CatalogQuery query;
  final String? highlightId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(sourcesCatalogNotifierProvider(query));

    return state.when(
      loading: () => const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (e, _) => SliverToBoxAdapter(
        child: _ErrorBlock(
          message: e.toString(),
          onRetry: () =>
              ref.invalidate(sourcesCatalogNotifierProvider(query)),
        ),
      ),
      data: (value) {
        final items = value.items;
        if (items.isEmpty) {
          return const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(14, 14, 14, 0),
              child: _EmptyBlock(
                title: 'Ничего не найдено',
                desc: 'Попробуйте изменить фильтр или поиск.',
              ),
            ),
          );
        }

        return SliverList.builder(
          itemCount: items.length + 1,
          itemBuilder: (context, i) {
            if (i == 0) {
              return Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 8),
                child: NFText.mono('ВСЕ ИСТОЧНИКИ · ${items.length}'),
              );
            }
            final idx = i - 1;
            final s = items[idx];
            return Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 6),
              child: _CatalogItem(
                source: s,
                highlighted: highlightId == s.id,
              ),
            );
          },
        );
      },
    );
  }
}

class _HiddenList extends ConsumerWidget {
  const _HiddenList({required this.platform, required this.query});

  final SourceType? platform;
  final String query;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(blockedSourcesNotifierProvider);
    return state.when(
      loading: () => const SliverToBoxAdapter(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (e, _) => SliverToBoxAdapter(
        child: _ErrorBlock(
          message: e.toString(),
          onRetry: () =>
              ref.invalidate(blockedSourcesNotifierProvider),
        ),
      ),
      data: (all) {
        final filtered = all.where((b) {
          if (platform != null && b.type != platform) return false;
          if (query.trim().isEmpty) return true;
          final q = query.toLowerCase();
          final name = b.name ?? '';
          return name.toLowerCase().contains(q) ||
              b.sourceId.toLowerCase().contains(q);
        }).toList();

        return SliverList.builder(
          itemCount: filtered.isEmpty ? 2 : filtered.length + 1,
          itemBuilder: (context, i) {
            if (i == 0) {
              return Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 8),
                child: NFText.mono('СКРЫТЫЕ · ${filtered.length}'),
              );
            }
            if (filtered.isEmpty) {
              return const Padding(
                padding: EdgeInsets.fromLTRB(14, 0, 14, 0),
                child: _HiddenEmpty(),
              );
            }
            final s = filtered[i - 1];
            return Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 6),
              child: _HiddenItem(
                source: s,
                onUnblock: () =>
                    _unblock(ref, context, s),
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _unblock(
    WidgetRef ref,
    BuildContext context,
    BlockedSource s,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(blockedSourcesNotifierProvider.notifier)
          .unblock(s.sourceId);
      messenger.showSnackBar(
        const SnackBar(content: Text('Источник снова отображается')),
      );
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('Не удалось: $e')),
      );
    }
  }
}

class _CatalogItem extends StatelessWidget {
  const _CatalogItem({required this.source, required this.highlighted});

  final Source source;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: highlighted
            ? NFColors.lime.withValues(alpha: 0.2)
            : NFColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: NFColors.hairline),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _PlatformGlyph(type: source.type),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  source.name,
                  style: const TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: NFColors.ink,
                  ),
                ),
                if (source.url != null) ...<Widget>[
                  const SizedBox(height: 1),
                  NFText.mono(source.url!),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HiddenItem extends StatelessWidget {
  const _HiddenItem({required this.source, required this.onUnblock});

  final BlockedSource source;
  final VoidCallback onUnblock;

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
          _PlatformGlyph(type: source.type),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              source.name?.isNotEmpty == true ? source.name! : source.sourceId,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: NFColors.ink,
              ),
            ),
          ),
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onUnblock,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: NFColors.lime,
                borderRadius: BorderRadius.circular(999),
              ),
              child: const Text(
                'Вернуть',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  color: NFColors.limeInk,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PlatformGlyph extends StatelessWidget {
  const _PlatformGlyph({required this.type});

  final SourceType? type;

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
      child: type != null
          ? Text(
              type!.shortLabel,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.4,
                color: NFColors.ink,
              ),
            )
          : const Icon(Icons.help_outline, size: 14, color: NFColors.mute),
    );
  }
}

class _HiddenEmpty extends StatelessWidget {
  const _HiddenEmpty();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(18, 28, 18, 28),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.br,
        border: Border.all(color: NFColors.hairline),
      ),
      child: Column(
        children: <Widget>[
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: NFColors.chipBg,
              borderRadius: BorderRadius.circular(12),
            ),
            alignment: Alignment.center,
            child: const NFIcon('eye-off', size: 20, color: NFColors.mute),
          ),
          const SizedBox(height: 10),
          const Text(
            'Ничего не скрыто',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: NFColors.ink,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Откройте карточку в ленте и выберите «Скрыть источник», '
            'чтобы убрать его материалы отсюда.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 13,
              color: NFColors.mute,
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyBlock extends StatelessWidget {
  const _EmptyBlock({required this.title, required this.desc});

  final String title;
  final String desc;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(18, 28, 18, 28),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: NFRadii.br,
        border: Border.all(color: NFColors.hairline),
      ),
      child: Column(
        children: <Widget>[
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: NFColors.ink,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            desc,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 13,
              color: NFColors.mute,
            ),
          ),
        ],
      ),
    );
  }
}

class _ErrorBlock extends StatelessWidget {
  const _ErrorBlock({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontFamily: 'Nunito',
              fontSize: 14,
              color: NFColors.mute,
            ),
          ),
          const SizedBox(height: 8),
          TextButton(onPressed: onRetry, child: const Text('Повторить')),
        ],
      ),
    );
  }
}
