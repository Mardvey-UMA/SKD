import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

/// Application entry point.
///
/// Nunito ships as a bundled `.ttf` (`assets/fonts/Nunito-Variable.ttf`),
/// so no runtime font fetch / precache is required — Flutter resolves the
/// family synchronously at first paint.
void main() {
  runZonedGuarded<void>(() {
    WidgetsFlutterBinding.ensureInitialized();
    FlutterError.onError = (FlutterErrorDetails details) {
      FlutterError.presentError(details);
      debugPrint('[FLUTTER-ERR] ${details.exceptionAsString()}');
      if (details.stack != null) {
        debugPrint(details.stack.toString());
      }
    };
    runApp(const ProviderScope(child: App()));
  }, (Object error, StackTrace stack) {
    debugPrint('[ZONE-ERR] $error');
    debugPrint(stack.toString());
  });
}
