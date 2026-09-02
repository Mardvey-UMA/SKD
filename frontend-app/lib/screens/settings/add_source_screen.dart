import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors/app_failures.dart';
import '../../core/sources/domain/source_type.dart';
import '../../features/add_source/domain/value_objects/source_input.dart';
import '../../features/add_source/presentation/providers/add_source_controller.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/icon_btn.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';

/// Add source — redesigned screen.
///
/// Mirrors `AddSourceScreen` in `design/reference/mockup/screens.jsx`:
/// back + title, a single rounded input, mono hint, detected-source
/// preview card, and a lime «Добавить» pill + «Отмена» link at the bottom.
/// Detection + submission delegate to the existing
/// [addSourceControllerProvider] so the YooKassa/backend flow is untouched.
class AddSourceScreen extends ConsumerStatefulWidget {
  const AddSourceScreen({super.key});

  @override
  ConsumerState<AddSourceScreen> createState() => _AddSourceScreenState();
}

class _AddSourceScreenState extends ConsumerState<AddSourceScreen> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onSuccess(AddSourceSuccess success) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Источник добавлен')),
    );
    context.pop();
  }

  void _onFailure(AddSourceFailed failed) {
    if (!mounted) return;
    final f = failed.failure;
    if (f is LimitReachedFailure && f.kind == 'source') {
      showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Достигнут лимит'),
          content: Text(
            'Вы добавили максимум ${f.limit} источников. Удалите один, '
            'чтобы добавить новый.',
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Отмена'),
            ),
            FilledButton(
              onPressed: () {
                Navigator.of(ctx).pop();
                context.pop();
                context.push('/my-additions');
              },
              child: const Text('К моим источникам'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<AddSourceState>(addSourceControllerProvider, (prev, next) {
      if (next is AddSourceSuccess && prev is! AddSourceSuccess) {
        _onSuccess(next);
      } else if (next is AddSourceFailed && prev is! AddSourceFailed) {
        _onFailure(next);
      }
    });

    final state = ref.watch(addSourceControllerProvider);
    final notifier = ref.read(addSourceControllerProvider.notifier);

    final bool isSubmitting = state is AddSourceSubmitting;
    final bool canSubmit =
        state is AddSourceDetected || state is AddSourceFailed;

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        bottom: false,
        child: Stack(
          children: <Widget>[
            ListView(
              padding: const EdgeInsets.fromLTRB(0, 10, 0, 180),
              children: <Widget>[
                Padding(
                  padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
                  child: Row(
                    children: <Widget>[
                      IconBtn(
                        iconName: 'back',
                        onTap: () => context.pop(),
                        semanticLabel: 'Назад',
                      ),
                      const SizedBox(width: 10),
                      const Expanded(
                        child: Text(
                          'Добавить источник',
                          style: TextStyle(
                            fontFamily: 'Nunito',
                            fontSize: 17,
                            fontWeight: FontWeight.w700,
                            letterSpacing: -0.3,
                            color: NFColors.ink,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(18, 14, 18, 0),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: NFColors.surface,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: NFColors.hairline),
                    ),
                    child: TextField(
                      controller: _controller,
                      onChanged: notifier.onInputChanged,
                      style: const TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 15,
                        color: NFColors.ink,
                      ),
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        hintText: 'Ссылка или @username',
                        hintStyle: TextStyle(
                          fontFamily: 'Nunito',
                          fontSize: 15,
                          color: NFColors.mute,
                        ),
                        contentPadding: EdgeInsets.symmetric(vertical: 14),
                      ),
                    ),
                  ),
                ),
                const Padding(
                  padding: EdgeInsets.fromLTRB(22, 8, 22, 0),
                  child: NFText.mono(
                    't.me/... · habr.com/hub/... · vc.ru/u/... или vc.ru/<раздел>',
                  ),
                ),
                if (state is AddSourceDetected)
                  _Detected(parsed: state.parsed),
                if (state is AddSourceSubmitting)
                  _Detected(parsed: state.parsed),
                if (state is AddSourceFailed && state.parsed != null)
                  _Detected(parsed: state.parsed!),
                if (state is AddSourceFailed)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 12, 18, 0),
                    child: _FailureBanner(failure: state.failure),
                  ),
              ],
            ),
            Positioned(
              left: 14,
              right: 14,
              bottom: 22,
              child: Column(
                children: <Widget>[
                  GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: canSubmit && !isSubmitting ? notifier.submit : null,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18,
                        vertical: 16,
                      ),
                      decoration: BoxDecoration(
                        color: canSubmit ? NFColors.lime : NFColors.surface2,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: <Widget>[
                          if (isSubmitting)
                            const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: NFColors.limeInk,
                              ),
                            )
                          else ...<Widget>[
                            Text(
                              'Добавить',
                              style: TextStyle(
                                fontFamily: 'Nunito',
                                fontSize: 15,
                                fontWeight: FontWeight.w700,
                                color: canSubmit
                                    ? NFColors.limeInk
                                    : NFColors.mute,
                              ),
                            ),
                            const SizedBox(width: 8),
                            NFIcon(
                              'check',
                              size: 15,
                              color: canSubmit
                                  ? NFColors.limeInk
                                  : NFColors.mute,
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 4),
                  TextButton(
                    onPressed: isSubmitting ? null : () => context.pop(),
                    child: const Text(
                      'Отмена',
                      style: TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: NFColors.ink2,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Detected extends StatelessWidget {
  const _Detected({required this.parsed});

  final SourceInput parsed;

  @override
  Widget build(BuildContext context) {
    final SourceType type = parsed.type;
    final String displayName = parsed.displayName;
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 18, 18, 0),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: NFRadii.br,
          border: Border.all(color: NFColors.hairline),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: NFColors.chipBg,
                borderRadius: BorderRadius.circular(8),
              ),
              alignment: Alignment.center,
              child: Text(
                type.shortLabel,
                style: const TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.4,
                  color: NFColors.ink,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  NFText.mono('ОБНАРУЖЕНО · ${type.wire}'),
                  const SizedBox(height: 3),
                  Text(
                    displayName,
                    style: const TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: NFColors.ink,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FailureBanner extends StatelessWidget {
  const _FailureBanner({required this.failure});

  final AppFailure failure;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: NFColors.warn.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: NFColors.warn.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: <Widget>[
          const NFIcon('close', size: 16, color: NFColors.warn),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              failure.message,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 13,
                color: NFColors.ink,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
