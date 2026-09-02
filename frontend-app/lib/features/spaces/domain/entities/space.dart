import 'package:equatable/equatable.dart';
import '../../../../core/sources/domain/space_color.dart';

class Space extends Equatable {
  const Space({
    required this.id,
    required this.name,
    required this.color,
    required this.sourceIds,
    required this.sourceCount,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String name;
  final SpaceColor color;
  final List<String> sourceIds;
  final int sourceCount;
  final DateTime createdAt;
  final DateTime updatedAt;

  Space copyWith({
    String? name,
    SpaceColor? color,
    List<String>? sourceIds,
    int? sourceCount,
  }) => Space(
    id: id,
    name: name ?? this.name,
    color: color ?? this.color,
    sourceIds: sourceIds ?? this.sourceIds,
    sourceCount: sourceCount ?? this.sourceCount,
    createdAt: createdAt,
    updatedAt: DateTime.now(),
  );

  @override
  List<Object?> get props =>
      [id, name, color, sourceIds, sourceCount, createdAt, updatedAt];
}
