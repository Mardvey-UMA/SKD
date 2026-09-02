import 'package:flutter/material.dart';
import '../../domain/models/article_card.dart';
import 'digest_card_widget.dart';
import 'image_article_card_widget.dart';
import 'text_article_card_widget.dart';

/// Dispatcher widget — routes to the correct card widget
/// based on the ArticleCard sealed subtype.
class ArticleCardWidget extends StatelessWidget {
  const ArticleCardWidget({super.key, required this.card});
  final ArticleCard card;

  @override
  Widget build(BuildContext context) {
    return switch (card) {
      ImageArticleCard() => ImageArticleCardWidget(
        card: card as ImageArticleCard,
      ),
      TextArticleCard() => TextArticleCardWidget(card: card as TextArticleCard),
      DigestCard() => DigestCardWidget(card: card as DigestCard),
    };
  }
}
