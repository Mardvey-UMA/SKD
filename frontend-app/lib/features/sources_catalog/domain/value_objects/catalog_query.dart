import 'package:equatable/equatable.dart';
import '../../../../core/sources/domain/source_type.dart';

class CatalogQuery extends Equatable {
  const CatalogQuery({this.type, this.q});

  final SourceType? type;
  final String? q;

  @override
  List<Object?> get props => [type, q];
}
