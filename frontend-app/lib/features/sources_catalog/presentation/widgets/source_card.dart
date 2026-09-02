import 'package:flutter/material.dart';
import '../../../../core/sources/presentation/widgets/source_type_badge.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/source.dart';

class SourceCard extends StatelessWidget {
  const SourceCard({
    super.key,
    required this.source,
    this.onTap,
    this.trailing,
  });

  final Source source;
  final VoidCallback? onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            SourceTypeBadge(type: source.type),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    source.name.isEmpty ? (source.url ?? '—') : source.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  if (source.url != null && source.url!.isNotEmpty)
                    Text(
                      source.url!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.textHint,
                      ),
                    ),
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: 8),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}
