import 'package:equatable/equatable.dart';
import 'source.dart';

class CatalogPage extends Equatable {
  const CatalogPage({
    required this.items,
    required this.cursor,
    required this.hasNext,
  });

  final List<Source> items;
  final String? cursor;
  final bool hasNext;

  @override
  List<Object?> get props => [items, cursor, hasNext];
}
