import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/sources/domain/space_color.dart';

void main() {
  group('SpaceColor.fromWire', () {
    test('parses each canonical value', () {
      for (final color in SpaceColor.values) {
        expect(SpaceColor.fromWire(color.wire), color);
      }
    });

    test('is case insensitive', () {
      expect(SpaceColor.fromWire('red'), SpaceColor.red);
      expect(SpaceColor.fromWire('Purple'), SpaceColor.purple);
    });

    test('falls back to blue on unknown / null', () {
      expect(SpaceColor.fromWire('GOLD'), SpaceColor.blue);
      expect(SpaceColor.fromWire(null), SpaceColor.blue);
    });

    test('exposes a Material color', () {
      for (final color in SpaceColor.values) {
        expect(color.material.a, greaterThan(0));
      }
    });
  });
}
