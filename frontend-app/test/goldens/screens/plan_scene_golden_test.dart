import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';

import '../golden_harness.dart';

/// Plan — scene golden. Free-plan variant with cycle toggle + price card
/// + feature list + comparison + «Оформить» CTA.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 21.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('plan scene golden — ${bp.name}', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _PlanScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_PlanScene),
        'plan_scene_${bp.name}.png',
      );
    });
  }
}

class _PlanScene extends StatelessWidget {
  const _PlanScene();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 14 : 32;
    final double contentMax = maxWidth <= 600 ? maxWidth : 560;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: contentMax),
            child: ListView(
              padding: EdgeInsets.fromLTRB(side, 14, side, 80),
              children: const <Widget>[
                NFText.mono('ТАРИФ'),
                SizedBox(height: 6),
                Text(
                  'Premium.',
                  style: TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 34,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -1.2,
                    color: NFColors.ink,
                    height: 1.0,
                  ),
                ),
                SizedBox(height: 18),
                _CycleToggle(),
                SizedBox(height: 14),
                _PriceCard(),
                SizedBox(height: 20),
                NFText.mono('В ПОДПИСКЕ'),
                SizedBox(height: 8),
                _FeatureRow(icon: 'layers', text: 'Безлимитные подборки'),
                _FeatureRow(icon: 'folder-plus', text: 'Пространства для источников'),
                _FeatureRow(icon: 'eye-off', text: 'Скрытие источников из ленты'),
                _FeatureRow(icon: 'spark', text: 'Без рекламы'),
                SizedBox(height: 24),
                _CtaPill(),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CycleToggle extends StatelessWidget {
  const _CycleToggle();

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: NFColors.hairline),
      ),
      padding: const EdgeInsets.all(4),
      child: Row(
        children: const <Widget>[
          Expanded(child: _CycleSegment(label: 'Месяц', active: false)),
          Expanded(child: _CycleSegment(label: 'Год · −20%', active: true)),
        ],
      ),
    );
  }
}

class _CycleSegment extends StatelessWidget {
  const _CycleSegment({required this.label, required this.active});

  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10),
      decoration: BoxDecoration(
        color: active ? NFColors.ink : const Color(0x00000000),
        borderRadius: BorderRadius.circular(999),
      ),
      alignment: Alignment.center,
      child: Text(
        label,
        style: TextStyle(
          fontFamily: 'Nunito',
          fontSize: 13,
          fontWeight: FontWeight.w700,
          color: active ? const Color(0xFFFFFFFF) : NFColors.ink,
        ),
      ),
    );
  }
}

class _PriceCard extends StatelessWidget {
  const _PriceCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: NFColors.ink, width: 1.5),
      ),
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const <Widget>[
          Text(
            '389 ₽/мес',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 32,
              fontWeight: FontWeight.w800,
              letterSpacing: -1,
              color: NFColors.ink,
              height: 1.0,
            ),
          ),
          SizedBox(height: 6),
          NFText.mono('ОПЛАТА ОДИН РАЗ В ГОД · 4 668 ₽'),
        ],
      ),
    );
  }
}

class _FeatureRow extends StatelessWidget {
  const _FeatureRow({required this.icon, required this.text});

  final String icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        children: <Widget>[
          NFIcon(icon, size: 16, color: NFColors.ink),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 14.5,
                color: NFColors.ink,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CtaPill extends StatelessWidget {
  const _CtaPill();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
      decoration: BoxDecoration(
        color: NFColors.accent,
        borderRadius: BorderRadius.circular(999),
      ),
      child: const Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Text(
            'Оформить Premium',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: NFColors.accentInk,
            ),
          ),
          SizedBox(width: 8),
          NFIcon('arrow-up-right', size: 15, color: NFColors.accentInk),
        ],
      ),
    );
  }
}
