import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';

import '../golden_harness.dart';

/// Settings — scene golden. «Настройки» heading + 4 grouped sections.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 21.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('settings scene golden — ${bp.name}', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _SettingsScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_SettingsScene),
        'settings_scene_${bp.name}.png',
      );
    });
  }
}

class _SettingsScene extends StatelessWidget {
  const _SettingsScene();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 14 : 32;
    final double contentMax = maxWidth <= 600 ? maxWidth : 620;

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
                Padding(
                  padding: EdgeInsets.fromLTRB(4, 0, 4, 18),
                  child: Text(
                    'Настройки',
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 40,
                      height: 0.95,
                      letterSpacing: -1.4,
                      fontWeight: FontWeight.w700,
                      color: NFColors.ink,
                    ),
                  ),
                ),
                _Section(
                  header: 'АККАУНТ',
                  rows: <_Row>[
                    _Row(title: 'Email', detail: 'chikernut213@gmail.com'),
                    _Row(title: 'Пароль'),
                    _Row(title: 'Выйти'),
                  ],
                ),
                _Section(
                  header: 'ПОДПИСКА',
                  rows: <_Row>[
                    _Row(title: 'Тариф', detail: 'Бесплатный'),
                    _Row(title: 'Перейти на Premium'),
                  ],
                ),
                _Section(
                  header: 'ИСТОЧНИКИ',
                  rows: <_Row>[
                    _Row(title: 'Мои источники', detail: '8'),
                    _Row(title: 'Добавить источник'),
                    _Row(title: 'Скрытые источники', detail: '2'),
                  ],
                ),
                _Section(
                  header: 'ПРИЛОЖЕНИЕ',
                  rows: <_Row>[
                    _Row(title: 'Интересы'),
                    _Row(title: 'Уведомления'),
                    _Row(title: 'О приложении', detail: 'v1.0.0'),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Row {
  const _Row({required this.title, this.detail});
  final String title;
  final String? detail;
}

class _Section extends StatelessWidget {
  const _Section({required this.header, required this.rows});

  final String header;
  final List<_Row> rows;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 0, 4, 10),
            child: NFText.mono(header),
          ),
          Container(
            decoration: BoxDecoration(
              color: NFColors.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: NFColors.hairline),
            ),
            child: Column(
              children: <Widget>[
                for (int i = 0; i < rows.length; i++) ...<Widget>[
                  _RowView(row: rows[i]),
                  if (i < rows.length - 1)
                    const Divider(
                      color: NFColors.hairline,
                      height: 1,
                      thickness: 1,
                      indent: 16,
                      endIndent: 16,
                    ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RowView extends StatelessWidget {
  const _RowView({required this.row});

  final _Row row;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Text(
              row.title,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 15.5,
                fontWeight: FontWeight.w600,
                color: NFColors.ink,
              ),
            ),
          ),
          if (row.detail != null) ...<Widget>[
            NFText.mono(row.detail!.toUpperCase()),
            const SizedBox(width: 8),
          ],
          const NFIcon('chevron', size: 14, color: NFColors.mute),
        ],
      ),
    );
  }
}
