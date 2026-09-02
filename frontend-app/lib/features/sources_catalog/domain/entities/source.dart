import 'package:equatable/equatable.dart';
import '../../../../core/sources/domain/source_type.dart';

class Source extends Equatable {
  const Source({
    required this.id,
    required this.type,
    required this.name,
    this.url,
    this.iconUrl,
    this.createdAt,
  });

  final String id;
  final SourceType type;
  final String name;
  final String? url;
  final String? iconUrl;
  final DateTime? createdAt;

  @override
  List<Object?> get props => [id, type, name, url, iconUrl, createdAt];
}
