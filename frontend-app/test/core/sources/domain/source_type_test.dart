import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/sources/domain/source_type.dart';

void main() {
  group('SourceType.fromWire', () {
    test('parses canonical wire values', () {
      expect(SourceType.fromWire('TELEGRAM'), SourceType.telegram);
      expect(SourceType.fromWire('HABR'), SourceType.habr);
      expect(SourceType.fromWire('VCRU'), SourceType.vcRu);
    });

    test('is case insensitive', () {
      expect(SourceType.fromWire('telegram'), SourceType.telegram);
      expect(SourceType.fromWire('habr'), SourceType.habr);
    });

    test('accepts legacy aliases', () {
      expect(SourceType.fromWire('VC.RU'), SourceType.vcRu);
      expect(SourceType.fromWire('VC_RU'), SourceType.vcRu);
      expect(SourceType.fromWire('TG'), SourceType.telegram);
    });

    test('returns null on unknown / null input', () {
      expect(SourceType.fromWire('RSS'), isNull);
      expect(SourceType.fromWire(null), isNull);
      expect(SourceType.fromWire(''), isNull);
    });

    test('round-trip via wire is stable', () {
      for (final t in SourceType.values) {
        expect(SourceType.fromWire(t.wire), t);
      }
    });
  });
}
