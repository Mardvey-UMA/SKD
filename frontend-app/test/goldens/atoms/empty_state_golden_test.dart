import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/empty_state.dart';

import '../golden_harness.dart';

/// State-matrix golden for `EmptyState` — renders one tile per accent
/// (`lime`, `accent`, `ink`). Tickers are muted so the rotating rings +
/// floating disk resolve to deterministic t=0 positions.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 8.
void main() {
  setUpAll(configureGoldenEnvironment);

  testWidgets('empty_state accent matrix golden', (WidgetTester tester) async {
    await pumpGolden(
      tester,
      const ColoredBox(
        color: NFColors.bg,
        child: TickerMode(
          enabled: false,
          child: _EmptyStateMatrix(),
        ),
      ),
      breakpoint: GoldenBreakpoint.mobile,
    );
    await expectGolden(
      find.byType(_EmptyStateMatrix),
      'empty_state_matrix.png',
    );
  });
}

class _EmptyStateMatrix extends StatelessWidget {
  const _EmptyStateMatrix();

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: const <Widget>[
          EmptyState(
            iconName: 'bookmark',
            accent: EmptyStateAccent.lime,
            title: 'Здесь пока пусто',
            desc: 'Когда сохраните материал — он появится тут.',
          ),
          SizedBox(height: 16),
          EmptyState(
            iconName: 'radar',
            accent: EmptyStateAccent.accent,
            title: 'Лента формируется',
            desc: 'Мы подбираем свежие материалы по вашим интересам.',
          ),
          SizedBox(height: 16),
          EmptyState(
            iconName: 'layers',
            accent: EmptyStateAccent.ink,
            title: 'Нет подборок',
            desc: 'Создайте первое пространство, чтобы начать собирать материалы.',
          ),
        ],
      ),
    );
  }
}
