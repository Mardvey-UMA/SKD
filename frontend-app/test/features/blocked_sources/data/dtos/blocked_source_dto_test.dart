import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/sources/domain/source_type.dart';
import 'package:frontend_app/features/blocked_sources/data/dtos/blocked_source_dto.dart';

void main() {
  group('BlockedSourceDto.fromJson / toDomain', () {
    test('enriched JSON with source_type and source_name maps correctly', () {
      final dto = BlockedSourceDto.fromJson({
        'source_id': 'abc-123',
        'source_type': 'HABR',
        'source_name': 'habr.com/flutter',
        'blocked_at': '2026-04-16T10:00:00Z',
      });
      final domain = dto.toDomain();

      expect(domain.sourceId, 'abc-123');
      expect(domain.type, SourceType.habr);
      expect(domain.name, 'habr.com/flutter');
    });

    test('null source_type and source_name produce null in domain', () {
      final dto = BlockedSourceDto.fromJson({
        'source_id': 'def-456',
        'source_type': null,
        'source_name': null,
        'blocked_at': '2026-04-16T10:00:00Z',
      });
      final domain = dto.toDomain();

      expect(domain.sourceId, 'def-456');
      expect(domain.type, isNull);
      expect(domain.name, isNull);
    });

    test('empty source_name produces null name in domain', () {
      final dto = BlockedSourceDto.fromJson({
        'source_id': 'ghi-789',
        'source_type': 'TELEGRAM',
        'source_name': '',
        'blocked_at': '2026-04-16T10:00:00Z',
      });
      final domain = dto.toDomain();

      expect(domain.type, SourceType.telegram);
      expect(domain.name, isNull);
    });

    test('VCRU source_type maps to SourceType.vcRu', () {
      final dto = BlockedSourceDto.fromJson({
        'source_id': 'jkl-000',
        'source_type': 'VCRU',
        'source_name': 'vc.ru/finance',
        'blocked_at': '2026-04-16T10:00:00Z',
      });
      final domain = dto.toDomain();

      expect(domain.type, SourceType.vcRu);
      expect(domain.name, 'vc.ru/finance');
    });

    test('unknown source_type produces null type', () {
      final dto = BlockedSourceDto.fromJson({
        'source_id': 'mno-111',
        'source_type': 'RSS',
        'source_name': 'some-feed',
        'blocked_at': '2026-04-16T10:00:00Z',
      });
      final domain = dto.toDomain();

      expect(domain.type, isNull);
    });

    test('supports alternative key names (type / name / blockedAt)', () {
      final dto = BlockedSourceDto.fromJson({
        'source_id': 'pqr-222',
        'type': 'HABR',
        'name': 'habr.com',
        'blockedAt': '2026-04-16T10:00:00Z',
      });
      final domain = dto.toDomain();

      expect(domain.type, SourceType.habr);
      expect(domain.name, 'habr.com');
    });
  });
}
