import 'package:flutter/material.dart';
import '../../../../core/sources/presentation/widgets/source_type_badge.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/blocked_source.dart';

class BlockedSourceTile extends StatelessWidget {
  const BlockedSourceTile({
    super.key,
    required this.source,
    required this.onUnblock,
  });

  final BlockedSource source;
  final VoidCallback onUnblock;

  @override
  Widget build(BuildContext context) {
    return Dismissible(
      key: ValueKey('blocked-${source.sourceId}'),
      direction: DismissDirection.endToStart,
      background: Container(
        color: AppColors.error,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.visibility_outlined, color: Colors.white),
            SizedBox(width: 8),
            Text(
              'Показывать',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
      onDismissed: (_) => onUnblock(),
      child: ListTile(
        tileColor: AppColors.surface,
        leading: SourceTypeBadge(type: source.type),
        title: Text(
          source.name?.isNotEmpty == true ? source.name! : source.sourceId,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: AppColors.textPrimary,
          ),
        ),
        subtitle: Text(
          _formatAgo(source.blockedAt),
          style: const TextStyle(fontSize: 12, color: AppColors.textHint),
        ),
        trailing: IconButton(
          tooltip: 'Показывать',
          icon: const Icon(Icons.undo, color: AppColors.primary),
          onPressed: onUnblock,
        ),
      ),
    );
  }

  String _formatAgo(DateTime t) {
    final diff = DateTime.now().difference(t);
    if (diff.inMinutes < 1) return 'только что';
    if (diff.inHours < 1) return 'скрыт ${diff.inMinutes} мин. назад';
    if (diff.inDays < 1) return 'скрыт ${diff.inHours} ч. назад';
    if (diff.inDays < 7) return 'скрыт ${diff.inDays} дн. назад';
    return 'скрыт ${t.day}.${t.month}.${t.year}';
  }
}
