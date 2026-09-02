import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/image_item.dart';
import 'package:frontend_app/ui/atoms/multi_image.dart';
import 'package:frontend_app/ui/atoms/stripe_placeholder.dart';

void main() {
  Future<void> pumpAt(
    WidgetTester tester,
    Widget child, {
    required Size size,
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(width: size.width, child: child),
        ),
      ),
    );
  }

  const ImageItem item = ImageItem(tone: StripeTone.ink, seed: 42);

  testWidgets('renders 3 StripePlaceholder cells at 390x200', (tester) async {
    await pumpAt(
      tester,
      const MultiImage(item: item),
      size: const Size(390, 200),
    );

    final Finder cells = find.byType(StripePlaceholder);
    expect(cells, findsNWidgets(3));

    final List<StripePlaceholder> widgets =
        tester.widgetList<StripePlaceholder>(cells).toList();
    expect(widgets[0].label, '1/3');
    expect(widgets[1].label, '2/3');
    expect(widgets[2].label, '3/3');
    expect(widgets[0].seed, 42);
    expect(widgets[1].seed, 52);
    expect(widgets[2].seed, 62);

    final Size leftCell = tester.getSize(cells.at(0));
    expect(leftCell.height, closeTo(200, 0.5));
    // flex 2 : (1+1) with a 4px gutter → roughly 2/3 minus a bit of gap.
    expect(leftCell.width, greaterThan(200));

    final Size topRight = tester.getSize(cells.at(1));
    final Size bottomRight = tester.getSize(cells.at(2));
    expect(topRight.width, closeTo(bottomRight.width, 0.5));
    expect(topRight.height, closeTo(bottomRight.height, 0.5));
    expect(topRight.height + bottomRight.height, lessThan(200));
  });

  testWidgets('renders 3 cells at 720x200 with fluid parent', (tester) async {
    await pumpAt(
      tester,
      const MultiImage(item: item),
      size: const Size(720, 200),
    );

    final Finder cells = find.byType(StripePlaceholder);
    expect(cells, findsNWidgets(3));

    final Size leftCell = tester.getSize(cells.at(0));
    final Size topRight = tester.getSize(cells.at(1));
    final Size bottomRight = tester.getSize(cells.at(2));

    expect(leftCell.height, closeTo(200, 0.5));
    expect(leftCell.width, greaterThan(topRight.width));
    expect(topRight.width, closeTo(bottomRight.width, 0.5));
    // Left + gap + right column must fill the 720px width.
    expect(leftCell.width + 4 + topRight.width, closeTo(720, 1.0));
  });
}
