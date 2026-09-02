import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/feature_flags.dart';
import '../../features/feed/presentation/providers/user_interaction_cache_provider.dart';
import '../../features/spaces/domain/entities/space.dart';
import '../../features/spaces/presentation/controllers/collection_tone_mapping.dart';
import '../../features/spaces/presentation/providers/spaces_providers.dart';
import '../../theme/colors.dart';
import '../../ui/atoms/nf_text.dart';
import '../../ui/atoms/stripe_placeholder.dart';
import 'collection_card.dart';

/// Collections (a.k.a. Spaces) — redesigned landing screen.
///
/// Visual renaming only: the backend module remains `spaces` (see
/// `features/spaces/`) and routes `/spaces/…` stay unchanged. The UI label
/// follows the JSX mock — «Ваши подборки.» with 3 system tiles + user
/// grid + a primary-pill CTA.
class CollectionsScreen extends ConsumerWidget {
  const CollectionsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userSpacesAsync = ref.watch(spacesListNotifierProvider);

    // Read counts from the in-memory interaction cache. The cache is
    // loaded once after login (see UserInteractionCacheNotifier.load) and
    // updated optimistically on every like/dislike/bookmark toggle, so
    // the tiles never flash «0» while collection pages refetch.
    final cache = ref.watch(userInteractionCacheNotifierProvider);
    final int bookmarksCount = cache.bookmarkedIds.length;
    final int likesCount = cache.likedIds.length;
    final int dislikesCount = cache.dislikedIds.length;

    final List<Space> userSpaces = userSpacesAsync.valueOrNull ?? const <Space>[];
    final int totalCount = 3 + userSpaces.length;
    final bool atLimit = userSpaces.length >= FeatureFlags.maxSpaces;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: CustomScrollView(
          slivers: <Widget>[
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 14, 18, 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    NFText.mono('$totalCount ПРОСТРАНСТВ'),
                    const SizedBox(height: 6),
                    const Text(
                      'Ваши\nподборки',
                      style: TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 40,
                        height: 0.95,
                        letterSpacing: -1.4,
                        fontWeight: FontWeight.w700,
                        color: NFColors.ink,
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Systemic section — 3 tiles
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 4),
              sliver: SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.only(left: 4, bottom: 8),
                  child: NFText.mono('СИСТЕМНЫЕ'),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 18),
              sliver: SliverToBoxAdapter(
                child: _SystemTilesGrid(
                  bookmarksCount: bookmarksCount,
                  likesCount: likesCount,
                  dislikesCount: dislikesCount,
                ),
              ),
            ),

            // User collections section
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 0, 18, 8),
              sliver: SliverToBoxAdapter(
                child: NFText.mono('ВАШИ'),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(14, 4, 14, 18),
              sliver: userSpacesAsync.when(
                loading: () => const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 32),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                ),
                error: (Object e, _) => SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 32),
                    child: Center(
                      child: TextButton(
                        onPressed: () =>
                            ref.invalidate(spacesListNotifierProvider),
                        child: Text('Ошибка: $e. Повторить?'),
                      ),
                    ),
                  ),
                ),
                data: (List<Space> spaces) {
                  if (spaces.isEmpty) {
                    return const SliverToBoxAdapter(
                      child: _EmptyUserCollections(),
                    );
                  }
                  return SliverGrid(
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      mainAxisSpacing: 12,
                      crossAxisSpacing: 12,
                      mainAxisExtent: 210,
                    ),
                    delegate: SliverChildBuilderDelegate(
                      (BuildContext context, int index) {
                        final Space s = spaces[index];
                        return CollectionCard(
                          title: s.name,
                          tone: CollectionToneMapping.toneFor(s.color),
                          showStats: false,
                          onTap: () => context.push('/spaces/${s.id}'),
                        );
                      },
                      childCount: spaces.length,
                    ),
                  );
                },
              ),
            ),

            // Bottom primary CTA
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 8, 18, 120),
              sliver: SliverToBoxAdapter(
                child: _CreateCollectionPill(
                  disabled: atLimit,
                  onTap: () {
                    if (atLimit) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            'Максимум ${FeatureFlags.maxSpaces} пространств. '
                            'Удалите одно, чтобы создать новое.',
                          ),
                        ),
                      );
                    } else {
                      context.push('/spaces/new');
                    }
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SystemTilesGrid extends StatelessWidget {
  const _SystemTilesGrid({
    required this.bookmarksCount,
    required this.likesCount,
    required this.dislikesCount,
  });

  final int bookmarksCount;
  final int likesCount;
  final int dislikesCount;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        // Heights chosen to fully contain `CollectionCard`:
        //   header block (8 padding + 120) + title (2×21×1.15 ≈ 48)
        //   + spacer (6) + mono caption (~14) + bottom padding (14)
        //   ≈ 220 px. Add a bit of slack to avoid debug banners on
        //   narrow text-scale configurations.
        SizedBox(
          height: 230,
          child: CollectionCard(
            title: 'Сохранённое',
            count: bookmarksCount,
            sources: 0,
            tone: StripeTone.lime,
            onTap: () => context.push('/shell/bookmarks?kind=bookmark'),
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: <Widget>[
            Expanded(
              child: SizedBox(
                height: 230,
                child: CollectionCard(
                  title: 'Понравилось',
                  count: likesCount,
                  sources: 0,
                  tone: StripeTone.accent,
                  onTap: () => context.push('/shell/bookmarks?kind=like'),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: SizedBox(
                height: 230,
                child: CollectionCard(
                  title: 'Не понравилось',
                  count: dislikesCount,
                  sources: 0,
                  tone: StripeTone.ink,
                  onTap: () => context.push('/shell/bookmarks?kind=dislike'),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _EmptyUserCollections extends StatelessWidget {
  const _EmptyUserCollections();

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 4),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: NFColors.hairline),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const <Widget>[
          NFText.mono('НЕТ ПОДБОРОК'),
          SizedBox(height: 6),
          Text(
            'Группируйте источники под свой режим чтения — по темам, '
            'платформам или настроению.',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 14.5,
              height: 1.45,
              color: NFColors.ink2,
            ),
          ),
        ],
      ),
    );
  }
}

class _CreateCollectionPill extends StatelessWidget {
  const _CreateCollectionPill({required this.onTap, required this.disabled});

  final VoidCallback onTap;
  final bool disabled;

  @override
  Widget build(BuildContext context) {
    final Color bg = disabled ? NFColors.mute2 : NFColors.ink;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 52,
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(999),
        ),
        alignment: Alignment.center,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: const <Widget>[
            Icon(Icons.add, size: 18, color: NFColors.accentInk),
            SizedBox(width: 8),
            Text(
              'Новое пространство',
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: NFColors.accentInk,
                letterSpacing: -0.2,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
