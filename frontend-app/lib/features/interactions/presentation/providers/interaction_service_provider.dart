import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../data/services/interaction_service.dart';

/// Singleton InteractionService, kept alive for the lifetime of the app.
///
/// Initialised on first access — registers WidgetsBindingObserver so that
/// events are flushed when the app goes to background.
final interactionServiceProvider = Provider<InteractionService>((ref) {
  final dio = ref.watch(dioProvider);
  final service = InteractionService(dio);
  service.init();
  ref.onDispose(service.dispose);
  return service;
});

/// App version from the platform (e.g. "1.2.3").
/// Resolved once at startup; null while loading or on error.
final packageInfoProvider = FutureProvider<PackageInfo>((ref) {
  return PackageInfo.fromPlatform();
});
