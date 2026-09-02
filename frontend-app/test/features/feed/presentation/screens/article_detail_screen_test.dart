import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_widget_from_html_core/flutter_widget_from_html_core.dart';
import 'package:frontend_app/features/feed/domain/models/content_item.dart';
import 'package:frontend_app/features/feed/domain/models/feed_state.dart';
import 'package:frontend_app/features/feed/domain/repositories/i_feed_repository.dart';
import 'package:frontend_app/features/feed/presentation/providers/feed_provider.dart';
import 'package:frontend_app/features/interactions/data/services/interaction_service.dart';
import 'package:frontend_app/screens/detail/detail_screen.dart';
import 'package:frontend_app/features/interactions/presentation/providers/interaction_service_provider.dart';

/// A no-op InteractionService that does not start any timers.
class _NoOpInteractionService extends InteractionService {
  _NoOpInteractionService() : super(Dio());

  @override
  void init() {} // skip timer + observer registration

  @override
  void dispose() {} // nothing to clean up
}

class _FakeRepo implements IFeedRepository {
  final ContentItem item;
  _FakeRepo(this.item);

  @override
  Future<ContentItem> getContentItem(String contentId) async => item;

  @override
  Future<FeedPage> getFeed({String? cursor, bool refresh = false}) =>
      throw UnimplementedError();

  @override
  Future<ContentInteractionStatus> getContentStatus(String contentId) =>
      throw UnimplementedError();

  @override
  Future<void> likeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> unlikeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> dislikeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> undislikeContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> bookmarkContent(String contentId) => throw UnimplementedError();

  @override
  Future<void> unbookmarkContent(String contentId) =>
      throw UnimplementedError();
}

Widget _buildApp(ContentItem item) {
  return ProviderScope(
    overrides: [
      feedRepositoryProvider.overrideWithValue(_FakeRepo(item)),
      interactionServiceProvider.overrideWithValue(_NoOpInteractionService()),
    ],
    child: const MaterialApp(home: DetailScreen(articleId: 'test-id')),
  );
}

void main() {
  // Disable URL-launch and other platform channels in test environment.
  TestWidgetsFlutterBinding.ensureInitialized();

  group('_ArticleBody fallback chain', () {
    testWidgets(
      'V1 (HTML): renders HtmlWidget when isHtml=true and contentHtml non-empty',
      (tester) async {
        final item = ContentItem(
          id: 'test-id',
          title: 'Habr Article',
          contentFormat: 'HTML',
          contentHtml: '<p>Hello world</p>',
          contentText: 'Hello world',
          previewText: 'Hello world',
          sourceType: 'HABR',
        );

        await tester.pumpWidget(_buildApp(item));
        // Wait for async provider to resolve.
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));

        expect(find.byType(HtmlWidget), findsOneWidget);
      },
    );

    testWidgets(
      'V2 (Telegram plain): renders Text when isHtml=false and contentText non-empty',
      (tester) async {
        final item = ContentItem(
          id: 'test-id',
          title: 'Telegram Post',
          contentFormat: 'PLAIN',
          contentHtml: '',
          contentText: 'Заголовок поста, обычный текст без HTML',
          previewText: 'Заголовок поста, обычный текст без HTML',
          sourceType: 'TELEGRAM',
          media: const [
            MediaItem(
              url: 'https://media.skd.io/telegram/photo1.jpg',
              type: 'image',
            ),
          ],
        );

        await tester.pumpWidget(_buildApp(item));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));

        expect(find.byType(HtmlWidget), findsNothing);
        expect(
          find.text('Заголовок поста, обычный текст без HTML'),
          findsOneWidget,
        );
      },
    );

    testWidgets(
      'V3 (RSS HTML no inline imgs): renders HtmlWidget for content_html body',
      (tester) async {
        final item = ContentItem(
          id: 'test-id',
          title: 'RSS Article',
          contentFormat: 'HTML',
          contentHtml: '<p>Article body without any inline images</p>',
          contentText: 'Article body without any inline images',
          previewText: 'Article body without any inline images',
          sourceType: 'RSS',
          media: const [
            MediaItem(url: 'https://media.skd.io/rss/thumb.jpg', type: 'image'),
          ],
        );

        await tester.pumpWidget(_buildApp(item));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));

        expect(find.byType(HtmlWidget), findsOneWidget);
      },
    );
  });
}
