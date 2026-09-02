import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../interactions/domain/models/interaction_event.dart';
import '../../../interactions/presentation/providers/interaction_service_provider.dart';
import '../providers/article_actions_provider.dart';

/// Compact icon-only action bar for an article: Like, Dislike, Save.
/// Active state fills the icon and tints it with the action color.
/// Each button has a 44x44 hit area for touch friendliness.
class ArticleActionBar extends ConsumerWidget {
  const ArticleActionBar({
    super.key,
    required this.articleId,
    this.feedRequestId,
    this.positionInFeed,
  });

  final String articleId;

  /// Feed-context fields plumbed from the parent card when the bar is
  /// rendered inside a feed item. Null for non-feed callers (e.g.
  /// bookmarks, detail) — in those cases rec-system receives an
  /// interaction without feed correlation, which is expected.
  final String? feedRequestId;
  final int? positionInFeed;

  String? _resolveDeviceType() {
    if (kIsWeb) return 'web';
    if (Platform.isAndroid) return 'android';
    return null;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final actionState = ref.watch(
      articleActionsNotifierProvider.select(
        (map) => map[articleId] ?? const ArticleActionState(),
      ),
    );
    final notifier = ref.read(articleActionsNotifierProvider.notifier);
    final interactionService = ref.read(interactionServiceProvider);
    final String? appVersion =
        ref.watch(packageInfoProvider).valueOrNull?.version;
    final String? deviceType = _resolveDeviceType();

    InteractionEvent event(InteractionAction action) => InteractionEvent(
          contentId: articleId,
          action: action,
          timestamp: DateTime.now(),
          feedRequestId: feedRequestId,
          positionInFeed: positionInFeed,
          deviceType: deviceType,
          appVersion: appVersion,
        );

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _ActionIconButton(
          tooltip: 'Нравится',
          icon: actionState.isLiked ? Icons.thumb_up : Icons.thumb_up_outlined,
          color: actionState.isLiked
              ? AppColors.actionLikeActive
              : AppColors.actionDefault,
          onTap: () {
            notifier.like(articleId);
            interactionService.trackEvent(event(InteractionAction.like));
          },
        ),
        const SizedBox(width: 8),
        _ActionIconButton(
          tooltip: 'Не нравится',
          icon: actionState.isDisliked
              ? Icons.thumb_down
              : Icons.thumb_down_outlined,
          color: actionState.isDisliked
              ? AppColors.error
              : AppColors.actionDefault,
          onTap: () {
            notifier.dislike(articleId);
            interactionService.trackEvent(event(InteractionAction.dislike));
          },
        ),
        const SizedBox(width: 8),
        _ActionIconButton(
          tooltip: actionState.isSaved ? 'Удалить из сохранённых' : 'Сохранить',
          icon: actionState.isSaved ? Icons.bookmark : Icons.bookmark_outline,
          color: actionState.isSaved
              ? AppColors.actionSaveActive
              : AppColors.actionDefault,
          onTap: () {
            notifier.toggleSave(articleId);
            interactionService.trackEvent(event(InteractionAction.bookmark));
          },
        ),
      ],
    );
  }
}

class _ActionIconButton extends StatelessWidget {
  const _ActionIconButton({
    required this.icon,
    required this.color,
    required this.tooltip,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: InkResponse(
        onTap: onTap,
        radius: 24,
        containedInkWell: false,
        child: SizedBox(
          width: 44,
          height: 44,
          child: Center(child: Icon(icon, size: 26, color: color)),
        ),
      ),
    );
  }
}
