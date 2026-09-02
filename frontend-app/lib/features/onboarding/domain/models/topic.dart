import 'package:equatable/equatable.dart';
import 'package:flutter/material.dart';

class Topic extends Equatable {
  const Topic({required this.id, required this.name, required this.icon});

  final String id;
  final String name;
  final String icon;

  /// Maps backend icon identifier to Flutter IconData.
  IconData get iconData {
    switch (icon) {
      // rec-system icon names (from categories table)
      case 'landmark':
        return Icons.account_balance;
      case 'chart-line':
        return Icons.trending_up;
      case 'laptop':
        return Icons.laptop;
      case 'flask':
        return Icons.science;
      case 'trophy':
        return Icons.emoji_events;
      case 'palette':
        return Icons.palette;
      case 'users':
        return Icons.people;
      case 'alert-triangle':
        return Icons.warning_amber;
      case 'globe':
        return Icons.public;
      case 'briefcase':
        return Icons.business_center;
      case 'dollar-sign':
        return Icons.attach_money;
      case 'book-open':
        return Icons.school;
      case 'heart':
        return Icons.favorite;
      case 'film':
        return Icons.movie;
      case 'alert-octagon':
        return Icons.gavel;
      case 'shield':
        return Icons.shield;
      case 'tree':
        return Icons.park;
      case 'truck':
        return Icons.directions_car;
      // API_CONTRACTS.md fallback names
      case 'sports':
        return Icons.sports;
      case 'science':
        return Icons.science;
      case 'politics':
        return Icons.account_balance;
      case 'economics':
        return Icons.trending_up;
      case 'culture':
        return Icons.theater_comedy;
      case 'society':
        return Icons.people;
      case 'business':
        return Icons.business_center;
      case 'finance':
        return Icons.attach_money;
      case 'health':
        return Icons.favorite;
      case 'entertainment':
        return Icons.movie;
      case 'education':
        return Icons.school;
      case 'world':
        return Icons.public;
      case 'alert':
        return Icons.warning_amber;
      case 'crime':
        return Icons.gavel;
      case 'military':
        return Icons.shield;
      case 'nature':
        return Icons.park;
      case 'transport':
        return Icons.directions_car;
      default:
        return Icons.article;
    }
  }

  @override
  List<Object?> get props => [id, name, icon];
}
