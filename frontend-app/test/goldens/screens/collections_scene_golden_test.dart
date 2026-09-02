import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/screens/collections/collection_card.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';
import 'package:frontend_app/ui/atoms/stripe_placeholder.dart';

import '../golden_harness.dart';

/// Collections (Spaces) — scene golden.
///
/// Recreates the Prompt 18 landing: «Ваши подборки.» title + three system
/// tiles (`Сохранённые`, `Лайки`, `Не интересно`) + user-collections grid.
/// No providers — real `CollectionsScreen` is ref.watched against spaces /
/// bookmark / like / dislike providers.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 18.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('collections scene golden — ${bp.name}',
        (WidgetTester tester) async {
      await pumpGolden(
        tester,
        const _CollectionsScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_CollectionsScene),
        'collections_scene_${bp.name}.png',
      );
    });
  }
}

class _CollectionsScene extends StatelessWidget {
  const _CollectionsScene();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final int columns = maxWidth <= 600
        ? 2
        : maxWidth <= 1200
            ? 3
            : 4;
    final double side = maxWidth <= 600 ? 18 : 32;

    return ColoredBox(
      color: NFColors.bg,
      child: SafeArea(
        bottom: false,
        child: SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(side, 24, side, 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Text(
                'Ваши подборки.',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 34,
                  letterSpacing: -1.2,
                  fontWeight: FontWeight.w700,
                  color: NFColors.ink,
                  height: 1.0,
                ),
              ),
              const SizedBox(height: 6),
              const NFText.mono('5 ПОДБОРОК'),
              const SizedBox(height: 18),
              _Grid(
                columns: columns,
                children: const <Widget>[
                  CollectionCard(
                    title: 'Сохранённые',
                    count: 24,
                    sources: 8,
                    tone: StripeTone.lime,
                  ),
                  CollectionCard(
                    title: 'Лайки',
                    count: 41,
                    sources: 12,
                    tone: StripeTone.accent,
                  ),
                  CollectionCard(
                    title: 'Не интересно',
                    count: 7,
                    sources: 4,
                    tone: StripeTone.ink,
                  ),
                  CollectionCard(
                    title: 'Работа и инженерия',
                    count: 18,
                    sources: 6,
                    tone: StripeTone.teal,
                  ),
                  CollectionCard(
                    title: 'Кино и сериалы',
                    count: 12,
                    sources: 3,
                    tone: StripeTone.rose,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Grid extends StatelessWidget {
  const _Grid({required this.columns, required this.children});

  final int columns;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    const double gap = 12;
    return LayoutBuilder(
      builder: (BuildContext ctx, BoxConstraints cons) {
        final double tileWidth =
            (cons.maxWidth - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: <Widget>[
            for (final Widget c in children)
              SizedBox(width: tileWidth, child: c),
          ],
        );
      },
    );
  }
}
