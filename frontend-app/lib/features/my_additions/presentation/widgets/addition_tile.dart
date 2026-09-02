import 'package:flutter/material.dart';
import '../../../../core/sources/presentation/widgets/source_type_badge.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/source_addition.dart';

class AdditionTile extends StatelessWidget {
  const AdditionTile({super.key, required this.addition});

  final SourceAddition addition;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      tileColor: AppColors.surface,
      leading: SourceTypeBadge(type: addition.type),
      title: Text(
        addition.name.isEmpty ? addition.sourceId : addition.name,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: AppColors.textPrimary,
        ),
      ),
      subtitle: Text(
        _ago(addition.addedAt),
        style: const TextStyle(fontSize: 12, color: AppColors.textHint),
      ),
    );
  }

  String _ago(DateTime t) {
    final diff = DateTime.now().difference(t);
    if (diff.inMinutes < 1) return 'только что';
    if (diff.inHours < 1) return 'добавлен ${diff.inMinutes} мин. назад';
    if (diff.inDays < 1) return 'добавлен ${diff.inHours} ч. назад';
    if (diff.inDays < 7) return 'добавлен ${diff.inDays} дн. назад';
    return 'добавлен ${t.day}.${t.month}.${t.year}';
  }
}
