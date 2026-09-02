import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/feed/domain/models/content_item.dart';

ContentItem _item({
  String? contentFormat,
  String? sourceType,
  String? content,
  String? contentHtml,
  String? contentText,
  String? previewText,
  String? description,
}) => ContentItem(
  id: 'id',
  title: 'title',
  contentFormat: contentFormat,
  sourceType: sourceType,
  content: content,
  contentHtml: contentHtml,
  contentText: contentText,
  previewText: previewText,
  description: description,
);

void main() {
  group('ContentItem.isHtml', () {
    test('returns true for "HTML" (uppercase)', () {
      expect(_item(contentFormat: 'HTML').isHtml, isTrue);
    });

    test('returns true for "html" (lowercase)', () {
      expect(_item(contentFormat: 'html').isHtml, isTrue);
    });

    test('returns false for legacy "text/html"', () {
      expect(_item(contentFormat: 'text/html').isHtml, isFalse);
    });

    test('returns false for null', () {
      expect(_item(contentFormat: null).isHtml, isFalse);
    });

    test('returns false for "PLAIN"', () {
      expect(_item(contentFormat: 'PLAIN').isHtml, isFalse);
    });
  });

  group('ContentItem.isTelegram', () {
    test('returns true for "TELEGRAM" (uppercase)', () {
      expect(_item(sourceType: 'TELEGRAM').isTelegram, isTrue);
    });

    test('returns true for "telegram" (lowercase)', () {
      expect(_item(sourceType: 'telegram').isTelegram, isTrue);
    });

    test('returns false for "HABR"', () {
      expect(_item(sourceType: 'HABR').isTelegram, isFalse);
    });

    test('returns false for null', () {
      expect(_item(sourceType: null).isTelegram, isFalse);
    });
  });

  group('ContentItem.bestPreview', () {
    test('returns previewText when available', () {
      final item = _item(previewText: 'preview', description: 'desc');
      expect(item.bestPreview, 'preview');
    });

    test('falls back to description when previewText is null', () {
      final item = _item(previewText: null, description: 'desc');
      expect(item.bestPreview, 'desc');
    });

    test('returns null when both are null', () {
      final item = _item(previewText: null, description: null);
      expect(item.bestPreview, isNull);
    });
  });

  group('ContentItem.bestContentHtml', () {
    test('returns contentHtml when available', () {
      final item = _item(
        contentHtml: '<p>html</p>',
        content: 'legacy',
        contentFormat: 'HTML',
      );
      expect(item.bestContentHtml, '<p>html</p>');
    });

    test('falls back to content when isHtml and contentHtml is null', () {
      final item = _item(
        contentHtml: null,
        content: '<p>legacy</p>',
        contentFormat: 'HTML',
      );
      expect(item.bestContentHtml, '<p>legacy</p>');
    });

    test('returns null when not isHtml and contentHtml is null', () {
      final item = _item(
        contentHtml: null,
        content: 'plain',
        contentFormat: 'PLAIN',
      );
      expect(item.bestContentHtml, isNull);
    });
  });

  group('ContentItem.bestContentText', () {
    test('returns contentText when available', () {
      final item = _item(
        contentText: 'plain text',
        content: 'legacy',
        contentFormat: 'PLAIN',
      );
      expect(item.bestContentText, 'plain text');
    });

    test('falls back to content when not isHtml and contentText is null', () {
      final item = _item(
        contentText: null,
        content: 'legacy plain',
        contentFormat: 'PLAIN',
      );
      expect(item.bestContentText, 'legacy plain');
    });

    test('returns null when isHtml and contentText is null', () {
      final item = _item(
        contentText: null,
        content: '<p>html</p>',
        contentFormat: 'HTML',
      );
      expect(item.bestContentText, isNull);
    });
  });
}
