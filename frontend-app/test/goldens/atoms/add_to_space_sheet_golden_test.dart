import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/sheets/add_to_space_sheet.dart';

import '../golden_harness.dart';

/// State-matrix golden for `AddToSpaceSheet` — renders both the empty-state
/// (no collections → helper banner) and populated-state (three tones).
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 9.
void main() {
  setUpAll(configureGoldenEnvironment);

  testWidgets('add_to_space_sheet empty golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      Stack(
        children: <Widget>[
          const Positioned.fill(child: ColoredBox(color: NFColors.bg)),
          AddToSpaceSheet(
            sourceTitle: 'VC.RU',
            collections: const <UserCollection>[],
            onClose: () {},
            onCreate: () {},
            onSelect: (_) {},
          ),
        ],
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await tester.pump(const Duration(milliseconds: 400));
    await expectGolden(
      find.byType(AddToSpaceSheet),
      'add_to_space_sheet_empty.png',
    );
  });

  testWidgets('add_to_space_sheet populated golden', (WidgetTester tester) async {
    const List<UserCollection> collections = <UserCollection>[
      UserCollection(id: 'c1', title: 'Дизайн', tone: CollectionTone.lime),
      UserCollection(id: 'c2', title: 'Продукт', tone: CollectionTone.accent),
      UserCollection(id: 'c3', title: 'Инженерия', tone: CollectionTone.teal),
    ];

    await pumpGolden(
      tester,
      Stack(
        children: <Widget>[
          const Positioned.fill(child: ColoredBox(color: NFColors.bg)),
          AddToSpaceSheet(
            sourceTitle: 'VC.RU',
            collections: collections,
            onClose: () {},
            onCreate: () {},
            onSelect: (_) {},
          ),
        ],
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await tester.pump(const Duration(milliseconds: 400));
    await expectGolden(
      find.byType(AddToSpaceSheet),
      'add_to_space_sheet_populated.png',
    );
  });
}
