import 'package:equatable/equatable.dart';
import 'package:flutter/material.dart';

class Collection extends Equatable {
  const Collection({
    required this.id,
    required this.name,
    required this.description,
    required this.iconEmoji,
    required this.iconColor,
    required this.itemCount,
  });

  final String id;
  final String name;
  final String description;
  final String iconEmoji;
  final Color iconColor;
  final int itemCount;

  @override
  List<Object?> get props => [
    id,
    name,
    description,
    iconEmoji,
    iconColor,
    itemCount,
  ];
}
