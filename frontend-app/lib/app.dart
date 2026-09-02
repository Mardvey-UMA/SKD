import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/router/app_router.dart';
import 'core/services/token_cache_provider.dart';
import 'features/interactions/presentation/providers/interaction_service_provider.dart';
import 'theme/radar_theme.dart';

/// Root application widget with GoRouter and dark theme.
///
/// Responsive layout (mobile / tablet / desktop) is owned by
/// `ResponsiveShell` mounted via `ShellRoute` inside [goRouterProvider] —
/// see `lib/ui/shell/responsive_shell.dart`. The old `PhoneAspectRatio`
/// dev wrapper was removed in redesign Prompt 13.
class App extends ConsumerWidget {
  const App({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(goRouterProvider);
    // Eagerly initialise token cache so auth interceptors have tokens
    // before the first request fires (BUG-015 fix).
    ref.watch(tokenCacheInitProvider);
    // Eagerly initialise interaction service so the WidgetsBindingObserver
    // is registered before any navigation occurs.
    ref.watch(interactionServiceProvider);

    return MaterialApp.router(
      title: 'Радар',
      debugShowCheckedModeBanner: false,
      theme: RadarTheme.light(),
      routerConfig: router,
    );
  }
}
