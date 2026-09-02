import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend_app/theme/colors.dart';
import 'package:frontend_app/ui/atoms/nf_icon.dart';
import 'package:frontend_app/ui/atoms/nf_input.dart';
import 'package:frontend_app/ui/atoms/nf_text.dart';

import '../golden_harness.dart';

/// Sources catalog — scene golden. Back pill + title + segmented toggle +
/// search + platform chips + list of system sources.
///
/// Spec: `design/reference/radar-redesign-prompts.md` § Prompt 21.
void main() {
  setUpAll(configureGoldenEnvironment);

  for (final GoldenBreakpoint bp in GoldenBreakpoint.all) {
    testWidgets('sources scene golden — ${bp.name}', (WidgetTester tester) async {
      await pumpGolden(
        tester,
        _SourcesScene(),
        breakpoint: bp,
      );
      await expectGolden(
        find.byType(_SourcesScene),
        'sources_scene_${bp.name}.png',
      );
    });
  }
}

class _SourcesScene extends StatelessWidget {
  _SourcesScene();

  final TextEditingController _searchCtrl = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final double maxWidth = MediaQuery.of(context).size.width;
    final double side = maxWidth <= 600 ? 14 : 32;
    final double contentMax = maxWidth <= 600 ? maxWidth : 720;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: contentMax),
            child: ListView(
              padding: EdgeInsets.fromLTRB(side, 14, side, 80),
              children: <Widget>[
                const NFText.mono('ИСТОЧНИКИ'),
                const SizedBox(height: 6),
                const Text(
                  'Каталог источников.',
                  style: TextStyle(
                    fontFamily: 'Nunito',
                    fontSize: 30,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -1,
                    color: NFColors.ink,
                    height: 1.0,
                  ),
                ),
                const SizedBox(height: 18),
                const _SegmentedToggle(),
                const SizedBox(height: 12),
                NFInput(
                  controller: _searchCtrl,
                  placeholder: 'Поиск по каталогу',
                  icon: 'search',
                ),
                const SizedBox(height: 12),
                const _PlatformChips(),
                const SizedBox(height: 12),
                const _SourceRow(
                  name: 'Хабр',
                  desc: 'Технические статьи и хабы',
                  handle: 'HABR.COM',
                ),
                const _SourceRow(
                  name: 'VC.RU',
                  desc: 'Бизнес, стартапы и маркетинг',
                  handle: 'VC.RU',
                ),
                const _SourceRow(
                  name: 'Design & UX',
                  desc: 'Канал о дизайне интерфейсов',
                  handle: '@DESIGNPUB',
                ),
                const _SourceRow(
                  name: 'Founders Talk',
                  desc: 'Разговоры с основателями',
                  handle: '@FOUNDERSTALK',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SegmentedToggle extends StatelessWidget {
  const _SegmentedToggle();

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
          Expanded(child: _ToggleSegment(label: 'Каталог', active: true)),
          Expanded(child: _ToggleSegment(label: 'Скрытые', active: false)),
        ],
      ),
    );
  }
}

class _ToggleSegment extends StatelessWidget {
  const _ToggleSegment({required this.label, required this.active});

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

class _PlatformChips extends StatelessWidget {
  const _PlatformChips();

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: <Widget>[
        _Chip(label: 'Все', active: true),
        _Chip(label: 'Habr', active: false),
        _Chip(label: 'VC.RU', active: false),
        _Chip(label: 'Telegram', active: false),
      ],
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.active});

  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color: active ? NFColors.ink : NFColors.chipBg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontFamily: 'Nunito',
          fontSize: 12.5,
          fontWeight: FontWeight.w600,
          color: active ? const Color(0xFFFFFFFF) : NFColors.ink,
        ),
      ),
    );
  }
}

class _SourceRow extends StatelessWidget {
  const _SourceRow({
    required this.name,
    required this.desc,
    required this.handle,
  });

  final String name;
  final String desc;
  final String handle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: NFColors.hairline),
        ),
        child: Row(
          children: <Widget>[
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: NFColors.chipBg,
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child: const NFIcon('feed', size: 18, color: NFColors.ink),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(
                    name,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: NFColors.ink,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    desc,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 12.5,
                      color: NFColors.mute,
                    ),
                  ),
                  const SizedBox(height: 2),
                  NFText.mono(handle),
                ],
              ),
            ),
            const SizedBox(width: 8),
            const NFIcon('plus', size: 16, color: NFColors.ink),
          ],
        ),
      ),
    );
  }
}
