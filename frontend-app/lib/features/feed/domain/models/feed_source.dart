import 'package:equatable/equatable.dart';

class FeedSource extends Equatable {
  const FeedSource({
    required this.id,
    required this.name,
    this.isSelected = false,
  });

  final String id;
  final String name;
  final bool isSelected;

  @override
  List<Object?> get props => [id, name, isSelected];
}
