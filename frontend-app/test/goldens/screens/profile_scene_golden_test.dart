import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';

import '../golden_harness.dart';

/// Profile — scene golden. Lime avatar tile + email + 6 rows.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 20.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('profile scene golden — ${bp.name}', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _ProfileScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_ProfileScene),
        'profile_scene_${bp.name}.png',
      );
    });
  }
}

class _ProfileScene extends StatelessWidget {
  const _ProfileScene();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 18 : 32;
    final double contentMax = maxWidth <= 600 ? maxWidth : 560;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: contentMax),
            child: ListView(
              padding: EdgeInsets.fromLTRB(side, 20, side, 80),
              children: const <Widget>[
                _Header(),
                SizedBox(height: 18),
                _Row(icon: 'bookmark', title: 'Сохранённое'),
                _RowDivider(),
                _Row(icon: 'thumb-up', title: 'Понравилось'),
                _RowDivider(),
                _Row(icon: 'thumb-down', title: 'Не понравилось'),
                _RowDivider(),
                _Row(icon: 'feed', title: 'Источники'),
                _RowDivider(),
                _Row(icon: 'spark', title: 'Тариф'),
                _RowDivider(),
                _Row(icon: 'gear', title: 'Настройки'),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        Container(
          width: 74,
          height: 74,
          decoration: BoxDecoration(
            color: NFColors.lime,
            borderRadius: BorderRadius.circular(18),
          ),
          alignment: Alignment.center,
          child: const Text(
            'C',
            style: TextStyle(
              fontFamily: 'Nunito',
              fontSize: 38,
              fontWeight: FontWeight.w800,
              letterSpacing: -1,
              color: NFColors.limeInk,
            ),
          ),
        ),
        const SizedBox(width: 14),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text(
                'chikernut213',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.6,
                  color: NFColors.ink,
                  height: 1.1,
                ),
              ),
              SizedBox(height: 4),
              NFText.mono('CHIKERNUT213@GMAIL.COM'),
            ],
          ),
        ),
      ],
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.icon, required this.title});

  final String icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 2),
      child: Row(
        children: <Widget>[
          NFIcon(icon, size: 18, color: NFColors.ink2),
          const SizedBox(width: 14),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 15.5,
                fontWeight: FontWeight.w600,
                color: NFColors.ink,
              ),
            ),
          ),
          const NFIcon('chevron', size: 16, color: NFColors.mute),
        ],
      ),
    );
  }
}

class _RowDivider extends StatelessWidget {
  const _RowDivider();

  @override
  Widget build(BuildContext context) {
    return const Divider(
      color: NFColors.hairline,
      height: 1,
      thickness: 1,
    );
  }
}
