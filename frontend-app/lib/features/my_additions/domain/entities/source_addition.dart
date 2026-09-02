import 'package:equatable/equatable.dart';
import '../../../../core/sources/domain/source_type.dart';

class SourceAddition extends Equatable {
  const SourceAddition({
    required this.sourceId,
    required this.type,
    required this.name,
    required this.addedAt,
  });

  final String sourceId;
  final SourceType type;
  final String name;
  final DateTime addedAt;

  @override
  List<Object?> get props => [sourceId, type, name, addedAt];
}

class MyAdditionsPage extends Equatable {
  const MyAdditionsPage({
    required this.items,
    required this.cursor,
    required this.hasNext,
  });

  final List<SourceAddition> items;
  final String? cursor;
  final bool hasNext;

  @override
  List<Object?> get props => [items, cursor, hasNext];
}
