import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/feed/data/dto/content_item_dto.dart';

void main() {
  group('ContentItemDto.fromJson', () {
    test('parses content_html field', () {
      final dto = ContentItemDto.fromJson({
        'id': 'abc',
        'title': 'Test',
        'content_html': '<p>Hello</p>',
      });
      expect(dto.contentHtml, '<p>Hello</p>');
    });

    test('parses content_text field', () {
      final dto = ContentItemDto.fromJson({
        'id': 'abc',
        'title': 'Test',
        'content_text': 'plain text',
      });
      expect(dto.contentText, 'plain text');
    });

    test('parses preview_text field', () {
      final dto = ContentItemDto.fromJson({
        'id': 'abc',
        'title': 'Test',
        'preview_text': 'short preview',
      });
      expect(dto.previewText, 'short preview');
    });

    test('all three new fields are null when absent', () {
      final dto = ContentItemDto.fromJson({'id': 'abc', 'title': 'Test'});
      expect(dto.contentHtml, isNull);
      expect(dto.contentText, isNull);
      expect(dto.previewText, isNull);
    });

    test('parses all new fields together', () {
      final dto = ContentItemDto.fromJson({
        'id': '1',
        'title': 'Article',
        'description': 'desc',
        'content_html': '<p>body</p>',
        'content_text': 'body',
        'preview_text': 'preview',
        'content_format': 'HTML',
        'source_type': 'HABR',
      });
      expect(dto.contentHtml, '<p>body</p>');
      expect(dto.contentText, 'body');
      expect(dto.previewText, 'preview');
    });

    test('toDomain passes new fields through', () {
      final dto = ContentItemDto.fromJson({
        'id': '1',
        'title': 'Article',
        'content_html': '<p>html</p>',
        'content_text': 'text',
        'preview_text': 'prev',
      });
      final item = dto.toDomain();
      expect(item.contentHtml, '<p>html</p>');
      expect(item.contentText, 'text');
      expect(item.previewText, 'prev');
    });
  });
}
