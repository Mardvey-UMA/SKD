import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/models/article_card.dart';
import 'article_action_bar.dart';

class ImageArticleCardWidget extends StatelessWidget {
  const ImageArticleCardWidget({super.key, required this.card});
  final ImageArticleCard card;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => context.push('/shell/feed/${card.id}'),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(12),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0A000000),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(12),
              ),
              child: AspectRatio(
                aspectRatio: 16 / 9,
                child: card.imageUrl.isNotEmpty
                    ? Image.network(
                        card.imageUrl,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => Container(
                          color: AppColors.surfaceLight,
                          child: const Center(
                            child: Icon(
                              Icons.broken_image_outlined,
                              size: 32,
                              color: AppColors.textHint,
                            ),
                          ),
                        ),
                      )
                    : Container(
                        color: AppColors.surfaceLight,
                        child: const Center(
                          child: Icon(
                            Icons.image_outlined,
                            size: 32,
                            color: AppColors.textHint,
                          ),
                        ),
                      ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (card.title.isNotEmpty) ...[
                    Text(
                      card.title,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  ArticleActionBar(articleId: card.id),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
