import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/ui/motion/page_transitions.dart';
import 'package:go_router/go_router.dart';

void main() {
  group('buildFadeRisePage', () {
    testWidgets('returns CustomTransitionPage with 260ms duration', (tester) async {
      final page = buildFadeRisePage(child: const Text('destination'));

      expect(page, isA<CustomTransitionPage<dynamic>>());
      expect(page.transitionDuration, NFMotion.pageDuration);
      expect(page.reverseTransitionDuration, NFMotion.pageDuration);
      expect(NFMotion.pageDuration, const Duration(milliseconds: 260));
    });

    testWidgets('combines FadeTransition with SlideTransition during enter',
        (tester) async {
      final router = GoRouter(
        initialLocation: '/',
        routes: [
          GoRoute(
            path: '/',
            pageBuilder: (ctx, state) =>
                buildFadeRisePage(child: const Text('home')),
          ),
          GoRoute(
            path: '/next',
            pageBuilder: (ctx, state) =>
                buildFadeRisePage(child: const Text('next')),
          ),
        ],
      );

      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      await tester.pumpAndSettle();
      expect(find.text('home'), findsOneWidget);

      router.go('/next');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 130));

      // Mid-flight both transitions must be live.
      expect(find.byType(FadeTransition), findsWidgets);
      expect(find.byType(SlideTransition), findsWidgets);

      await tester.pumpAndSettle();
      expect(find.text('next'), findsOneWidget);
    });
  });

  group('NFFadeRisePageRoute', () {
    testWidgets('pushes with fade+rise transitions', (tester) async {
      final navKey = GlobalKey<NavigatorState>();
      await tester.pumpWidget(
        MaterialApp(
          navigatorKey: navKey,
          home: const Scaffold(body: Text('root')),
        ),
      );

      navKey.currentState!.push(
        NFFadeRisePageRoute<void>(
          builder: (_) => const Scaffold(body: Text('pushed')),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.byType(FadeTransition), findsWidgets);
      expect(find.byType(SlideTransition), findsWidgets);

      await tester.pumpAndSettle();
      expect(find.text('pushed'), findsOneWidget);
    });
  });
}
