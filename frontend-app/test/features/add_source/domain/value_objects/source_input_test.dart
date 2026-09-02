import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/sources/domain/source_type.dart';
import 'package:frontend_app/features/add_source/domain/value_objects/source_input.dart';

void main() {
  group('SourceInput.tryParse — Telegram', () {
    test('@handle', () {
      final r = SourceInput.tryParse('@lovely_news');
      expect(r?.type, SourceType.telegram);
      expect(r?.params, {'username': 'lovely_news'});
      expect(r?.displayName, '@lovely_news');
    });

    test('t.me/<user>', () {
      final r = SourceInput.tryParse('https://t.me/lovely_news');
      expect(r?.type, SourceType.telegram);
      expect(r?.params['username'], 'lovely_news');
    });

    test('telegram.me/<user>', () {
      final r = SourceInput.tryParse('http://telegram.me/foo_bar_baz');
      expect(r?.type, SourceType.telegram);
      expect(r?.params['username'], 'foo_bar_baz');
    });

    test('rejects too-short handle', () {
      expect(SourceInput.tryParse('@ab'), isNull);
    });
  });

  group('SourceInput.tryParse — Habr', () {
    test('hub with locale', () {
      final r = SourceInput.tryParse('https://habr.com/ru/hub/flutter');
      expect(r?.type, SourceType.habr);
      expect(r?.params['hub_slug'], 'flutter');
    });

    test('hub without locale', () {
      final r = SourceInput.tryParse('https://habr.com/hub/kotlin');
      expect(r?.type, SourceType.habr);
      expect(r?.params['hub_slug'], 'kotlin');
    });
  });

  group('SourceInput.tryParse — VC.RU', () {
    test('user id', () {
      final r = SourceInput.tryParse('https://vc.ru/u/123456');
      expect(r?.type, SourceType.vcRu);
      expect(r?.params['user_id'], '123456');
    });

    test('blog slug', () {
      final r = SourceInput.tryParse('https://vc.ru/design');
      expect(r?.type, SourceType.vcRu);
      expect(r?.params['blog_slug'], 'design');
    });
  });

  group('SourceInput.tryParse — rejects', () {
    test('empty / whitespace', () {
      expect(SourceInput.tryParse(''), isNull);
      expect(SourceInput.tryParse('   '), isNull);
    });

    test('unsupported host', () {
      expect(SourceInput.tryParse('https://example.com/foo'), isNull);
      expect(SourceInput.tryParse('twitter.com/elonmusk'), isNull);
    });
  });
}
