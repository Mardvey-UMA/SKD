import '../../../../core/sources/domain/source_type.dart';

/// Classifies a raw user-entered URL or username into a typed source input.
class SourceInput {
  const SourceInput({
    required this.type,
    required this.params,
    required this.displayName,
  });

  final SourceType type;
  final Map<String, dynamic> params;
  final String displayName;

  static SourceInput? tryParse(String raw) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty) return null;

    final normalised = trimmed.replaceFirst(RegExp(r'^https?://'), '');

    // Telegram: @username  or  t.me/<username>  or  telegram.me/<username>
    if (trimmed.startsWith('@')) {
      final username = trimmed.substring(1).trim();
      if (_isValidTgHandle(username)) {
        return SourceInput(
          type: SourceType.telegram,
          params: {'username': username},
          displayName: '@$username',
        );
      }
    }

    final tgRegex = RegExp(r'^(?:t\.me|telegram\.me)/(@?)([A-Za-z0-9_]+)$');
    final tgMatch = tgRegex.firstMatch(normalised);
    if (tgMatch != null) {
      final username = tgMatch.group(2) ?? '';
      if (_isValidTgHandle(username)) {
        return SourceInput(
          type: SourceType.telegram,
          params: {'username': username},
          displayName: '@$username',
        );
      }
    }

    // Habr hub: habr.com/ru/hub/<slug>/...
    final habrRegex = RegExp(
      r'^habr\.com/(?:[a-z]{2}/)?hub/([A-Za-z0-9_-]+)',
    );
    final habrMatch = habrRegex.firstMatch(normalised);
    if (habrMatch != null) {
      final slug = habrMatch.group(1)!;
      return SourceInput(
        type: SourceType.habr,
        params: {'hub_slug': slug},
        displayName: 'habr.com/hub/$slug',
      );
    }

    // VC.RU: vc.ru/u/<id>  or  vc.ru/<blog_slug>
    final vcUser = RegExp(r'^vc\.ru/u/([0-9]+)').firstMatch(normalised);
    if (vcUser != null) {
      final id = vcUser.group(1)!;
      return SourceInput(
        type: SourceType.vcRu,
        params: {'user_id': id},
        displayName: 'vc.ru/u/$id',
      );
    }
    final vcBlog = RegExp(
      r'^vc\.ru/([A-Za-z0-9_-]+)$',
    ).firstMatch(normalised);
    if (vcBlog != null) {
      final slug = vcBlog.group(1)!;
      return SourceInput(
        type: SourceType.vcRu,
        params: {'blog_slug': slug},
        displayName: 'vc.ru/$slug',
      );
    }

    return null;
  }

  static bool _isValidTgHandle(String s) =>
      s.length >= 3 &&
      s.length <= 64 &&
      RegExp(r'^[A-Za-z0-9_]+$').hasMatch(s);
}
