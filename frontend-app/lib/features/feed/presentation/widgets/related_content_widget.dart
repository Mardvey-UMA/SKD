import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/config/media_config.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/models/content_item.dart';
import '../providers/feed_provider.dart';

/// Horizontal scrollable list of related content.
/// Shows compact card: small image + title (2 lines).
class RelatedContentWidget extends StatelessWidget {
  const RelatedContentWidget({
    super.key,
    required this.relatedIds,
    this.onSeeAll,
  });

  final List<String> relatedIds;

  /// Optional handler for the "Смотреть все" button. When null the button
  /// is hidden.
  final VoidCallback? onSeeAll;

  @override
  Widget build(BuildContext context) {
    if (relatedIds.isEmpty) return const SizedBox.shrink();

    // Show up to 5 items in the preview; the rest are reachable via "See all".
    final ids = relatedIds.take(5).toList();
    final total = relatedIds.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: Row(
            children: [
              const Expanded(
                child: Text(
                  'Похожие материалы',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textPrimary,
                  ),
                ),
              ),
              if (onSeeAll != null)
                _SeeAllButton(total: total, onTap: onSeeAll!),
            ],
          ),
        ),
        SizedBox(
          height: 140,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: ids.length,
            separatorBuilder: (_, _) => const SizedBox(width: 10),
            itemBuilder: (context, index) =>
                _RelatedItemCard(contentId: ids[index]),
          ),
        ),
      ],
    );
  }
}

class _SeeAllButton extends StatelessWidget {
  const _SeeAllButton({required this.total, required this.onTap});

  final int total;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Смотреть все ($total)',
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(width: 2),
            const Icon(Icons.arrow_forward, size: 14, color: AppColors.primary),
          ],
        ),
      ),
    );
  }
}

class _RelatedItemCard extends ConsumerWidget {
  const _RelatedItemCard({required this.contentId});

  final String contentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final itemAsync = ref.watch(contentItemProvider(contentId));

    return itemAsync.when(
      loading: () => const _RelatedCardSkeleton(),
      error: (_, _) => const SizedBox.shrink(),
      data: (item) => _RelatedCardContent(item: item),
    );
  }
}

class _RelatedCardContent extends StatelessWidget {
  const _RelatedCardContent({required this.item});

  final ContentItem item;

  @override
  Widget build(BuildContext context) {
    final meta = _metaText(item);
    return GestureDetector(
      onTap: () => context.push('/shell/feed/${item.id}'),
      child: Container(
        width: 150,
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(10),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0A000000),
              blurRadius: 4,
              offset: Offset(0, 2),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (MediaConfig.resolve(item.firstImage?.url) != null)
              SizedBox(
                height: 80,
                width: double.infinity,
                child: CachedNetworkImage(
                  imageUrl: MediaConfig.resolve(item.firstImage!.url)!,
                  fit: BoxFit.cover,
                  placeholder: (_, _) =>
                      Container(color: AppColors.surfaceLight),
                  errorWidget: (_, _, _) => Container(
                    color: AppColors.surfaceLight,
                    child: const Icon(
                      Icons.broken_image_outlined,
                      size: 20,
                      color: AppColors.textHint,
                    ),
                  ),
                ),
              )
            else
              Container(
                height: 80,
                width: double.infinity,
                color: AppColors.surfaceLight,
                child: const Icon(
                  Icons.article_outlined,
                  size: 24,
                  color: AppColors.textHint,
                ),
              ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(6),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        item.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w500,
                          color: AppColors.textPrimary,
                          height: 1.3,
                        ),
                      ),
                    ),
                    if (meta != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        meta,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 9,
                          fontWeight: FontWeight.w400,
                          color: AppColors.textSecondary,
                          height: 1.2,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  static String? _metaText(ContentItem item) {
    final parts = <String>[];
    if (item.authorName != null && item.authorName!.trim().isNotEmpty) {
      parts.add(item.authorName!.trim());
    }
    if (item.publishedAt != null) {
      parts.add(_formatRelativeDate(item.publishedAt!));
    }
    if (parts.isEmpty) return null;
    return parts.join(' • ');
  }

  static String _formatRelativeDate(DateTime date) {
    final now = DateTime.now();
    final diff = now.difference(date);
    if (diff.inMinutes < 1) return 'сейчас';
    if (diff.inMinutes < 60) return '${diff.inMinutes} мин';
    if (diff.inHours < 24) return '${diff.inHours} ч';
    if (diff.inDays < 7) return '${diff.inDays} д';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()} нед';
    if (diff.inDays < 365) return '${(diff.inDays / 30).floor()} мес';
    return '${(diff.inDays / 365).floor()} г';
  }
}

class _RelatedCardSkeleton extends StatelessWidget {
  const _RelatedCardSkeleton();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 150,
      decoration: BoxDecoration(
        color: AppColors.surfaceLight,
        borderRadius: BorderRadius.circular(10),
      ),
    );
  }
}
