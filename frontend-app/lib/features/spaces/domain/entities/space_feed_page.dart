import 'package:equatable/equatable.dart';
import '../../../feed/domain/models/content_item.dart';

class SpaceFeedPage extends Equatable {
  const SpaceFeedPage({
    required this.items,
    required this.cursor,
    required this.hasNext,
  });

  final List<ContentItem> items;
  final String? cursor;
  final bool hasNext;

  @override
  List<Object?> get props => [items, cursor, hasNext];
}
