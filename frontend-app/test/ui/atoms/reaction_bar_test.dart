import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/atoms/reaction_bar.dart';

void main() {
  Future<void> pump(WidgetTester tester, Widget child) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Center(child: child),
      ),
    );
  }

  group('ReactionBar', () {
    testWidgets(
      'tap on Like while Dislike active does NOT flip Dislike inside the atom',
      (tester) async {
        int likeCalls = 0;
        int dislikeCalls = 0;
        await pump(
          tester,
          ReactionBar(
            isLiked: false,
            isDisliked: true,
            isBookmarked: false,
            onLike: () => likeCalls++,
            onDislike: () => dislikeCalls++,
            onBookmark: () {},
          ),
        );

        await tester.tap(find.bySemanticsLabel('Нравится'));
        await tester.pumpAndSettle();

        expect(likeCalls, 1);
        expect(dislikeCalls, 0);
      },
    );

    testWidgets('all 8 state permutations render without exception',
        (tester) async {
      for (int mask = 0; mask < 8; mask++) {
        final bool like = (mask & 1) != 0;
        final bool dislike = (mask & 2) != 0;
        final bool bookmark = (mask & 4) != 0;

        await pump(
          tester,
          ReactionBar(
            isLiked: like,
            isDisliked: dislike,
            isBookmarked: bookmark,
            onLike: () {},
            onDislike: () {},
            onBookmark: () {},
          ),
        );
        await tester.pumpAndSettle();

        expect(tester.takeException(), isNull,
            reason: 'permutation L=$like D=$dislike B=$bookmark threw');
        expect(find.byType(ReactionBar), findsOneWidget);
      }
    });

    testWidgets('onLike/onDislike/onBookmark called exactly once per tap',
        (tester) async {
      int likeCalls = 0;
      int dislikeCalls = 0;
      int bookmarkCalls = 0;

      await pump(
        tester,
        ReactionBar(
          isLiked: false,
          isDisliked: false,
          isBookmarked: false,
          onLike: () => likeCalls++,
          onDislike: () => dislikeCalls++,
          onBookmark: () => bookmarkCalls++,
        ),
      );

      await tester.tap(find.bySemanticsLabel('Нравится'));
      await tester.pumpAndSettle();
      await tester.tap(find.bySemanticsLabel('Не нравится'));
      await tester.pumpAndSettle();
      await tester.tap(find.bySemanticsLabel('В закладки'));
      await tester.pumpAndSettle();

      expect(likeCalls, 1);
      expect(dislikeCalls, 1);
      expect(bookmarkCalls, 1);
    });
  });
}
