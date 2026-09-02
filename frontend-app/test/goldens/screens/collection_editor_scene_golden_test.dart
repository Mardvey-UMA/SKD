import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_input.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';
import 'package:frontend_app/ui/atoms/stripe_placeholder.dart';
import 'package:frontend_app/ui/atoms/tone_swatch.dart';

import '../golden_harness.dart';

/// Collection editor — scene golden.
///
/// Reproduces the visual anatomy of `CollectionEditorScreen` (Prompt 18):
/// header back pill + title input + description input + tone-swatch row.
/// Real screen depends on `spaces_providers`; for the golden only the
/// visual chrome matters.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 18.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('collection_editor scene golden — ${bp.name}',
        (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _CollectionEditorScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_CollectionEditorScene),
        'collection_editor_scene_${bp.name}.png',
      );
    });
  }
}

class _CollectionEditorScene extends StatelessWidget {
  const _CollectionEditorScene();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 18 : 32;
    final double contentMax = maxWidth <= 600 ? maxWidth : 640;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: contentMax),
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(side, 24, side, 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  const NFText.mono('НОВАЯ ПОДБОРКА'),
                  const SizedBox(height: 10),
                  const Text(
                    'Создайте\nпространство.',
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 30,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -1,
                      height: 1.05,
                      color: NFColors.ink,
                    ),
                  ),
                  const SizedBox(height: 20),
                  NFInput(
                    controller: TextEditingController(text: 'Работа и инженерия'),
                    placeholder: 'Название',
                    icon: 'layers',
                  ),
                  const SizedBox(height: 12),
                  NFInput(
                    controller: TextEditingController(
                      text: 'Лонгриды и разборы по архитектуре фронтенда',
                    ),
                    placeholder: 'Описание (опционально)',
                    icon: 'feed',
                  ),
                  const SizedBox(height: 20),
                  const NFText.mono('ЦВЕТ'),
                  const SizedBox(height: 10),
                  const _ToneRow(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ToneRow extends StatelessWidget {
  const _ToneRow();

  @override
  Widget build(BuildContext context) {
    const List<StripeTone> tones = <StripeTone>[
      StripeTone.lime,
      StripeTone.accent,
      StripeTone.warn,
      StripeTone.violet,
      StripeTone.teal,
      StripeTone.rose,
      StripeTone.ink,
    ];
    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: <Widget>[
        for (int i = 0; i < tones.length; i++)
          ToneSwatch(
            tone: tones[i],
            isSelected: i == 1,
            onTap: () {},
          ),
      ],
    );
  }
}
