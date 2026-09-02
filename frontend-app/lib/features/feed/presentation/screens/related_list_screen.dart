import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../providers/feed_provider.dart';
import '../widgets/content_card_widget.dart';

/// Full-screen vertical list of related articles for a parent article.
/// Reuses [ContentCardWidget] from feed for consistent appearance.
class RelatedListScreen extends ConsumerWidget {
  const RelatedListScreen({super.key, required this.parentArticleId});

  final String parentArticleId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final parentAsync = ref.watch(contentItemProvider(parentArticleId));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
          onPressed: () => context.pop(),
        ),
        title: parentAsync.maybeWhen(
          data: (parent) {
            // Telegram / VC posts ship with empty `title` — fall back to
            // the source name / author, else just a plain heading.
            final String label;
            final String raw = parent.title.trim();
            if (raw.isNotEmpty) {
              label = 'Похожие на «${_truncate(raw, 30)}»';
            } else {
              final String? src = parent.sourceName?.trim().isNotEmpty == true
                  ? parent.sourceName!.trim()
                  : (parent.authorName?.trim().isNotEmpty == true
                      ? parent.authorName!.trim()
                      : null);
              label = src != null
                  ? 'Похожие на «${_truncate(src, 30)}»'
                  : 'Похожие материалы';
            }
            return Text(
              label,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
              overflow: TextOverflow.ellipsis,
            );
          },
          orElse: () => const Text(
            'Похожие материалы',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
        ),
      ),
      body: parentAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => const _RelatedError(),
        data: (parent) {
          if (parent.relatedIds.isEmpty) {
            return const _RelatedEmpty();
          }
          return _RelatedListBody(relatedIds: parent.relatedIds);
        },
      ),
    );
  }

  static String _truncate(String value, int maxLen) {
    if (value.length <= maxLen) return value;
    return '${value.substring(0, maxLen).trimRight()}…';
  }
}

class _RelatedListBody extends ConsumerWidget {
  const _RelatedListBody({required this.relatedIds});

  final List<String> relatedIds;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      itemCount: relatedIds.length,
      itemBuilder: (context, index) {
        final id = relatedIds[index];
        return Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: _RelatedItemTile(contentId: id),
        );
      },
    );
  }
}

/// Tile that fetches a single ContentItem by id and renders a feed card.
class _RelatedItemTile extends ConsumerWidget {
  const _RelatedItemTile({required this.contentId});

  final String contentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final itemAsync = ref.watch(contentItemProvider(contentId));

    return itemAsync.when(
      loading: () => const _TileSkeleton(),
      error: (_, _) => const SizedBox.shrink(),
      data: (item) => RepaintBoundary(child: ContentCardWidget(item: item)),
    );
  }
}

class _TileSkeleton extends StatelessWidget {
  const _TileSkeleton();

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 220,
      decoration: BoxDecoration(
        color: AppColors.surfaceLight,
        borderRadius: BorderRadius.circular(12),
      ),
    );
  }
}

class _RelatedEmpty extends StatelessWidget {
  const _RelatedEmpty();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.layers_outlined, size: 56, color: AppColors.textHint),
            SizedBox(height: 16),
            Text(
              'Похожих материалов нет',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w500,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _RelatedError extends StatelessWidget {
  const _RelatedError();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(32),
        child: Text(
          'Не удалось загрузить похожие материалы',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 14, color: AppColors.textSecondary),
        ),
      ),
    );
  }
}
