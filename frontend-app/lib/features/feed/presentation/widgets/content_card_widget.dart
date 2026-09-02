import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:visibility_detector/visibility_detector.dart';
import '../../../../core/config/feature_flags.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../blocked_sources/presentation/providers/blocked_sources_provider.dart';
import '../../../interactions/domain/models/interaction_event.dart';
import '../../../interactions/presentation/providers/interaction_service_provider.dart';
import '../../../subscription/presentation/providers/subscription_provider.dart';
import '../../domain/models/content_item.dart';
import '../providers/feed_provider.dart';
import 'article_action_bar.dart';
import 'media_gallery_widget.dart';

/// Telegram-style content card with viewport-based interaction tracking:
/// - visible ≥2s → `view` event
/// - visible <2s then scrolled away → `scroll_past` event
class ContentCardWidget extends ConsumerStatefulWidget {
  const ContentCardWidget({super.key, required this.item});

  final ContentItem item;

  @override
  ConsumerState<ContentCardWidget> createState() => _ContentCardWidgetState();
}

class _ContentCardWidgetState extends ConsumerState<ContentCardWidget> {
  DateTime? _visibleSince;
  bool _eventSent = false;

  void _onVisibilityChanged(VisibilityInfo info) {
    if (!mounted) return;
    if (info.visibleFraction > 0.5) {
      // Card is at least half visible — start timer
      _visibleSince ??= DateTime.now();
    } else {
      // Card scrolled away
      final since = _visibleSince;
      if (since != null && !_eventSent) {
        final durationSec =
            DateTime.now().difference(since).inMilliseconds / 1000.0;
        final service = ref.read(interactionServiceProvider);
        if (durationSec >= 2.0) {
          service.trackEvent(
            InteractionEvent(
              contentId: widget.item.id,
              action: InteractionAction.impression,
              durationSec: durationSec,
              timestamp: since,
            ),
          );
        } else {
          service.trackEvent(
            InteractionEvent(
              contentId: widget.item.id,
              action: InteractionAction.close,
              durationSec: durationSec,
              timestamp: since,
            ),
          );
        }
        _eventSent = true;
      }
      _visibleSince = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return VisibilityDetector(
      key: ValueKey('card-${widget.item.id}'),
      onVisibilityChanged: _onVisibilityChanged,
      child: GestureDetector(
        onTap: () => context.push('/shell/feed/${widget.item.id}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (widget.item.hasMedia)
              MediaGalleryWidget(media: widget.item.media),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (widget.item.displayTitle.isNotEmpty)
                        Expanded(
                          child: Text(
                            widget.item.displayTitle,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                              height: 1.3,
                            ),
                          ),
                        )
                      else
                        const Spacer(),
                      if (widget.item.sourceId != null)
                        _SourceMenuButton(item: widget.item),
                    ],
                  ),
                  if (widget.item.bestPreview != null &&
                      widget.item.bestPreview!.isNotEmpty) ...[
                    if (widget.item.displayTitle.isNotEmpty)
                      const SizedBox(height: 4),
                    Text(
                      widget.item.bestPreview!,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w400,
                        color: AppColors.textSecondary,
                        height: 1.4,
                      ),
                    ),
                  ],
                  const SizedBox(height: 6),
                  _MetaRow(item: widget.item),
                  const SizedBox(height: 4),
                  ArticleActionBar(articleId: widget.item.id),
                ],
              ),
            ),
            // Bottom fade: card color dissolves into app background
            Container(
              height: 24,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    AppColors.surface,    // 100% card color at the top edge
                    AppColors.background, // 100% app background at the bottom
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MetaRow extends StatelessWidget {
  const _MetaRow({required this.item});

  final ContentItem item;

  @override
  Widget build(BuildContext context) {
    final parts = <String>[];
    if (item.authorName != null) parts.add(item.authorName!);
    if (item.publishedAt != null) {
      parts.add(_formatTime(item.publishedAt!));
    }
    if (item.metadata?.readTimeMinutes != null) {
      parts.add('${item.metadata!.readTimeMinutes} min read');
    }

    if (parts.isEmpty) return const SizedBox.shrink();

    return Text(
      parts.join(' · '),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(
        fontSize: 11,
        color: AppColors.textHint,
        fontWeight: FontWeight.w400,
      ),
    );
  }

  String _formatTime(DateTime dt) {
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return '${dt.day}.${dt.month}.${dt.year}';
  }
}

/// 3-dot menu on each feed card exposing "Скрыть источник" (all users) and
/// "Добавить в пространство" (premium).
class _SourceMenuButton extends ConsumerWidget {
  const _SourceMenuButton({required this.item});

  final ContentItem item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isPremium =
        ref.watch(subscriptionNotifierProvider).valueOrNull?.isPremium ??
            false;
    final canHide =
        !FeatureFlags.requireSubscriptionForHideSource || isPremium;
    if (!canHide && !isPremium) return const SizedBox.shrink();

    return PopupMenuButton<_SourceMenuAction>(
      padding: EdgeInsets.zero,
      tooltip: 'Действия с источником',
      color: Colors.white,
      surfaceTintColor: Colors.white,
      icon: const Icon(
        Icons.more_horiz,
        color: AppColors.textHint,
        size: 20,
      ),
      onSelected: (action) => _onAction(context, ref, action),
      itemBuilder: (_) => [
        if (canHide)
          PopupMenuItem(
            value: _SourceMenuAction.hide,
            child: Row(
              children: [
                Icon(Icons.visibility_off_outlined,
                    size: 18, color: Colors.red.shade400),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    item.sourceName?.isNotEmpty == true
                        ? 'Скрыть ${item.sourceName}'
                        : 'Скрыть источник',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        color: Colors.red.shade400, fontSize: 14),
                  ),
                ),
              ],
            ),
          ),
        if (isPremium)
          PopupMenuItem(
            value: _SourceMenuAction.addToSpace,
            child: Row(
              children: [
                const Icon(Icons.dashboard_customize_outlined,
                    size: 18, color: Colors.black87),
                const SizedBox(width: 8),
                const Text('Добавить в пространство',
                    style: TextStyle(
                        color: Colors.black87, fontSize: 14)),
              ],
            ),
          ),
      ],
    );
  }

  Future<void> _onAction(
    BuildContext context,
    WidgetRef ref,
    _SourceMenuAction action,
  ) async {
    switch (action) {
      case _SourceMenuAction.hide:
        await _hide(context, ref);
      case _SourceMenuAction.addToSpace:
        if (!context.mounted) return;
        await context.push('/spaces/new');
    }
  }

  Future<void> _hide(BuildContext context, WidgetRef ref) async {
    final sourceId = item.sourceId;
    if (sourceId == null) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Обновите ленту для скрытия источника')),
        );
      }
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    final type = SourceType.fromWire(item.sourceType);
    final displayName = item.sourceName?.isNotEmpty == true
        ? item.sourceName!
        : (type?.shortLabel ?? 'Источник');
    try {
      await ref
          .read(blockedSourcesNotifierProvider.notifier)
          .block(sourceId, cachedName: displayName, cachedType: type);
      // Server blocked + invalidated Valkey cache. Now refresh the feed
      // from server to get a clean list without this source.
      await ref.read(feedNotifierProvider.notifier).refresh();
      if (context.mounted) {
        messenger.showSnackBar(
          const SnackBar(content: Text('Источник скрыт')),
        );
      }
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('Не удалось скрыть: $e')),
      );
    }
  }
}

enum _SourceMenuAction { hide, addToSpace }
