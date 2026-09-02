import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/core/errors/app_failures.dart';
import 'package:frontend_app/features/auth/data/models/token_pair.dart';
import 'package:frontend_app/features/auth/domain/repositories/i_auth_repository.dart';
import 'package:frontend_app/features/auth/presentation/providers/auth_repository_provider.dart';
import 'package:frontend_app/features/auth/presentation/screens/email_verification_code_screen.dart';
import 'package:go_router/go_router.dart';

class _StubAuthRepository implements IAuthRepository {
  bool verifySuccess = true;
  Object? verifyError;

  @override
  Future<void> verifyEmailByCode(String email, String code) async {
    if (!verifySuccess) throw verifyError ?? const InvalidCodeFailure();
  }

  @override
  Future<void> resendVerificationCode(String email) async {}

  @override
  Future<({String email, String message})> register(
          String email, String password) =>
      throw UnimplementedError();
  @override
  Future<TokenPair> login(String email, String password) =>
      throw UnimplementedError();
  @override
  Future<TokenPair> refresh(String refreshToken) => throw UnimplementedError();
  @override
  Future<void> logout() => throw UnimplementedError();
  @override
  Future<void> verifyEmail(String token) => throw UnimplementedError();
  @override
  Future<void> requestPasswordReset(String email) => throw UnimplementedError();
  @override
  Future<void> resetPassword(String token, String newPassword) =>
      throw UnimplementedError();
  @override
  Future<void> changePassword(String currentPassword, String newPassword) =>
      throw UnimplementedError();
  @override
  Future<String?> getAccessToken() async => null;
}

Widget _buildScreen({
  _StubAuthRepository? repo,
  String email = 'test@example.com',
  GoRouter? router,
}) {
  final stub = repo ?? _StubAuthRepository();
  return ProviderScope(
    overrides: [authRepositoryProvider.overrideWithValue(stub)],
    child: router != null
        ? MaterialApp.router(routerConfig: router)
        : MaterialApp(
            home: EmailVerificationCodeScreen(email: email),
          ),
  );
}

void main() {
  group('EmailVerificationCodeScreen', () {
    testWidgets('shows email in subtitle', (tester) async {
      await tester.pumpWidget(_buildScreen(email: 'user@example.com'));
      await tester.pump();
      expect(find.text('Код подтверждения'), findsOneWidget);
      expect(find.textContaining('user@example.com'), findsOneWidget);
    });

    testWidgets('Verify button is disabled with < 6 digits', (tester) async {
      await tester.pumpWidget(_buildScreen());
      await tester.pump();

      // Enter only 3 digits
      final fields = find.byType(TextField);
      await tester.enterText(fields.at(0), '1');
      await tester.pump();
      await tester.enterText(fields.at(1), '2');
      await tester.pump();
      await tester.enterText(fields.at(2), '3');
      await tester.pump();

      final button = tester.widget<ElevatedButton>(
        find.byType(ElevatedButton),
      );
      expect(button.onPressed, isNull);
    });

    testWidgets('shows error message on INVALID_CODE', (tester) async {
      final repo = _StubAuthRepository()
        ..verifySuccess = false
        ..verifyError = const InvalidCodeFailure();

      await tester.pumpWidget(_buildScreen(repo: repo));
      await tester.pump();

      // Enter all 6 digits
      final fields = find.byType(TextField);
      for (int i = 0; i < 6; i++) {
        await tester.enterText(fields.at(i), '${i + 1}');
        await tester.pump();
      }

      // Tap verify button
      await tester.tap(find.byType(ElevatedButton));
      await tester.pumpAndSettle();

      expect(find.textContaining('неверно'), findsOneWidget);
      expect(find.text('Попробовать снова'), findsOneWidget);
    });

    testWidgets('resend link is rendered below button', (tester) async {
      await tester.pumpWidget(_buildScreen());
      await tester.pump();
      expect(find.text('Отправить повторно'), findsOneWidget);
    });
  });
}
