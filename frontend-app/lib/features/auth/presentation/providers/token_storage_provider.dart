import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../data/services/token_storage_service_impl.dart';
import '../../domain/services/i_token_storage_service.dart';

/// Provider for FlutterSecureStorage instance.
final flutterSecureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    webOptions: WebOptions(
      dbName: 'ContentFlowAuth',
      publicKey: 'ContentFlowAuth',
    ),
  );
});

/// Provider for token storage service.
final tokenStorageProvider = Provider<ITokenStorageService>((ref) {
  final storage = ref.watch(flutterSecureStorageProvider);
  return TokenStorageServiceImpl(storage);
});
