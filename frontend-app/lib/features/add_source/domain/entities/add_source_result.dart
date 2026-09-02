import '../../../sources_catalog/domain/entities/source.dart';

class AddSourceResult {
  const AddSourceResult({required this.source, required this.wasExisting});

  final Source source;
  final bool wasExisting;
}
