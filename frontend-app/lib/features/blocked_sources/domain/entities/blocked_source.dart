import 'package:equatable/equatable.dart';
import '../../../../core/sources/domain/source_type.dart';

class BlockedSource extends Equatable {
  const BlockedSource({
    required this.sourceId,
    required this.type,
    required this.name,
    required this.blockedAt,
  });

  final String sourceId;
  final SourceType? type;
  final String? name;
  final DateTime blockedAt;

  @override
  List<Object?> get props => [sourceId, type, name, blockedAt];
}
