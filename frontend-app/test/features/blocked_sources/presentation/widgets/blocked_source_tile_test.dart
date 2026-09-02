import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/features/blocked_sources/domain/entities/blocked_source.dart';
import 'package:frontend_app/features/blocked_sources/presentation/widgets/blocked_source_tile.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  group('BlockedSourceTile — null name/type rendering', () {
    testWidgets('shows sourceId when name is null', (tester) async {
      final source = BlockedSource(
        sourceId: 'uuid-1234-abcd',
        type: null,
        name: null,
        blockedAt: DateTime.utc(2026, 4, 16),
      );

      await tester.pumpWidget(_wrap(
        BlockedSourceTile(source: source, onUnblock: () {}),
      ));

      expect(find.text('uuid-1234-abcd'), findsOneWidget);
    });

    testWidgets('shows name when name is non-null', (tester) async {
      final source = BlockedSource(
        sourceId: 'uuid-9999',
        type: null,
        name: '@some_channel',
        blockedAt: DateTime.utc(2026, 4, 16),
      );

      await tester.pumpWidget(_wrap(
        BlockedSourceTile(source: source, onUnblock: () {}),
      ));

      expect(find.text('@some_channel'), findsOneWidget);
    });
  });
}

