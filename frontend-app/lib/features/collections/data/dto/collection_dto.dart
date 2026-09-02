import 'package:flutter/material.dart';
import '../../domain/models/collection.dart';

class CollectionDto {
  const CollectionDto({
    required this.id,
    required this.name,
    required this.description,
    required this.iconEmoji,
    required this.iconColorHex,
    required this.itemCount,
  });

  final String id;
  final String name;
  final String description;
  final String iconEmoji;
  final int iconColorHex;
  final int itemCount;

  factory CollectionDto.fromJson(Map<String, dynamic> json) => CollectionDto(
    id: json['id'] as String,
    name: json['name'] as String,
    description: json['description'] as String,
    iconEmoji: json['iconEmoji'] as String,
    iconColorHex: json['iconColorHex'] as int,
    itemCount: json['itemCount'] as int,
  );

  Collection toDomain() => Collection(
    id: id,
    name: name,
    description: description,
    iconEmoji: iconEmoji,
    iconColor: Color(iconColorHex),
    itemCount: itemCount,
  );
}
