import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/onboarding/domain/models/topic.dart';
import '../../features/onboarding/presentation/providers/onboarding_provider.dart';
import '../../features/onboarding/presentation/providers/topics_provider.dart';
import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../theme/typography.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/nf_text.dart';
import 'data/interests.dart';

/// Topics — onboarding interest picker backed by the real backend.
///
/// Categories and the min/max selection window come from
/// `GET /api/users/me/categories` (see [topicsNotifierProvider]).
/// The client owns **only** the chip style (emoji + background) via
/// [resolveInterestStyle] — labels and ids are never hard-coded.
class TopicsScreen extends ConsumerStatefulWidget {
  const TopicsScreen({super.key});

  static const int totalSteps = 3;
  static const int currentStep = 2;

  @override
  ConsumerState<TopicsScreen> createState() => _TopicsScreenState();
}

class _TopicsScreenState extends ConsumerState<TopicsScreen> {
  /// Selected backend category ids (lowercase Russian strings).
  final Set<String> _selectedIds = <String>{};
  bool _saving = false;

  void _toggle(String id, int maxSelect) {
    setState(() {
      if (_selectedIds.contains(id)) {
        _selectedIds.remove(id);
      } else if (_selectedIds.length < maxSelect) {
        _selectedIds.add(id);
      }
    });
  }

  Future<void> _continue(int minSelect, int maxSelect) async {
    final int count = _selectedIds.length;
    if (count < minSelect || count > maxSelect) return;
    setState(() => _saving = true);
    try {
      final repo = ref.read(onboardingRepositoryProvider);
      await repo.completeOnboarding(_selectedIds.toList(), <String>[]);
      ref.invalidate(onboardingStatusProvider);
      if (mounted) context.go('/shell/feed');
    } catch (e) {
      final msg = e.toString();
      if (msg.contains('Conflict') ||
          msg.contains('409') ||
          msg.contains('already')) {
        ref.invalidate(onboardingStatusProvider);
        if (mounted) context.go('/shell/feed');
        return;
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Не удалось сохранить. Попробуйте снова.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final topicsAsync = ref.watch(topicsNotifierProvider);

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: _TopicsFrame(
        child: topicsAsync.when(
          data: (response) => _TopicsBody(
            categories: response.categories,
            minSelect: response.minSelect,
            maxSelect: response.maxSelect,
            selectedIds: _selectedIds,
            saving: _saving,
            onToggle: (id) => _toggle(id, response.maxSelect),
            onBack: () {
              if (context.canPop()) {
                context.pop();
              } else {
                context.go('/login');
              }
            },
            onContinue: () =>
                _continue(response.minSelect, response.maxSelect),
          ),
          loading: () => const _TopicsLoading(),
          error: (err, _) => _TopicsError(
            onRetry: () => ref.invalidate(topicsNotifierProvider),
          ),
        ),
      ),
    );
  }
}

/// Tablet / desktop focus-mode wrapper — 520-wide centred panel.
class _TopicsFrame extends StatelessWidget {
  const _TopicsFrame({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final bp = context.breakpoint;
    if (bp == Breakpoint.mobile) {
      return SafeArea(child: child);
    }
    return SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Container(
            decoration: BoxDecoration(
              color: NFColors.bg,
              borderRadius: BorderRadius.circular(NFRadii.radiusLg),
              border: Border.all(color: NFColors.hairline),
            ),
            clipBehavior: Clip.antiAlias,
            child: child,
          ),
        ),
      ),
    );
  }
}

class _TopicsLoading extends StatelessWidget {
  const _TopicsLoading();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: SizedBox(
        width: 28,
        height: 28,
        child: CircularProgressIndicator(
          strokeWidth: 2.4,
          valueColor: AlwaysStoppedAnimation<Color>(NFColors.ink),
        ),
      ),
    );
  }
}

class _TopicsError extends StatelessWidget {
  const _TopicsError({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const NFText.h2(
              'Не удалось загрузить категории',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            NFText.body(
              'Проверьте подключение и попробуйте снова.',
              textAlign: TextAlign.center,
              color: NFColors.mute,
            ),
            const SizedBox(height: 20),
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: onRetry,
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                decoration: BoxDecoration(
                  color: NFColors.ink,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  'Повторить',
                  style: NFTypography.h2.copyWith(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0,
                    color: NFColors.accentInk,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TopicsBody extends StatelessWidget {
  const _TopicsBody({
    required this.categories,
    required this.minSelect,
    required this.maxSelect,
    required this.selectedIds,
    required this.saving,
    required this.onToggle,
    required this.onBack,
    required this.onContinue,
  });

  final List<Topic> categories;
  final int minSelect;
  final int maxSelect;
  final Set<String> selectedIds;
  final bool saving;
  final ValueChanged<String> onToggle;
  final VoidCallback onBack;
  final VoidCallback onContinue;

  bool get _canContinue =>
      selectedIds.length >= minSelect &&
      selectedIds.length <= maxSelect &&
      !saving;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        ListView(
          padding: const EdgeInsets.fromLTRB(20, 62, 20, 140),
          children: <Widget>[
            _HeaderRow(onBack: onBack),
            const SizedBox(height: 26),
            const NFText.mono('ПЕРВИЧНАЯ НАСТРОЙКА'),
            const SizedBox(height: 6),
            NFText.display(
              'Что вам\nинтересно?',
              breakpoint: context.breakpoint,
            ),
            const SizedBox(height: 8),
            Text(
              'Выберите несколько тем — лента подстроится.',
              style: NFTypography.body.copyWith(fontSize: 14.5),
            ),
            const SizedBox(height: 20),
            _CounterBadge(
              selected: selectedIds.length,
              minSelect: minSelect,
              maxSelect: maxSelect,
              canContinue: _canContinue,
            ),
            const SizedBox(height: 18),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: categories
                  .map(
                    (c) => _InterestChip(
                      topic: c,
                      style: resolveInterestStyle(c.id),
                      selected: selectedIds.contains(c.id),
                      atCap: !selectedIds.contains(c.id) &&
                          selectedIds.length >= maxSelect,
                      onTap: () => onToggle(c.id),
                    ),
                  )
                  .toList(growable: false),
            ),
          ],
        ),
        Positioned(
          left: 16,
          right: 16,
          bottom: 22,
          child: _ContinueBar(
            enabled: _canContinue,
            saving: saving,
            selectedCount: selectedIds.length,
            minSelect: minSelect,
            onContinue: onContinue,
          ),
        ),
      ],
    );
  }
}

class _HeaderRow extends StatelessWidget {
  const _HeaderRow({required this.onBack});

  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onBack,
          child: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: NFColors.surface,
              shape: BoxShape.circle,
              border: Border.all(color: NFColors.hairline),
            ),
            alignment: Alignment.center,
            child: const NFIcon('back', size: 17, color: NFColors.ink),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Row(
            children: List<Widget>.generate(
              TopicsScreen.totalSteps,
              (int i) => Expanded(
                child: Padding(
                  padding: EdgeInsets.only(left: i == 0 ? 0 : 4),
                  child: Container(
                    height: 3,
                    decoration: BoxDecoration(
                      color: i < TopicsScreen.currentStep
                          ? NFColors.ink
                          : NFColors.surface2,
                      borderRadius: BorderRadius.circular(3),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(width: 12),
        NFText.mono('${TopicsScreen.currentStep} / ${TopicsScreen.totalSteps}'),
      ],
    );
  }
}

class _CounterBadge extends StatelessWidget {
  const _CounterBadge({
    required this.selected,
    required this.minSelect,
    required this.maxSelect,
    required this.canContinue,
  });

  final int selected;
  final int minSelect;
  final int maxSelect;
  final bool canContinue;

  @override
  Widget build(BuildContext context) {
    final Color dotColor =
        canContinue ? NFColors.lime : const Color(0xFFFFC9C2);
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: NFColors.ink,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(
                color: dotColor,
                borderRadius: BorderRadius.circular(6),
              ),
            ),
            const SizedBox(width: 6),
            NFText.mono(
              '$selected / $maxSelect · МИН. $minSelect',
              color: dotColor,
            ),
          ],
        ),
      ),
    );
  }
}

class _InterestChip extends StatelessWidget {
  const _InterestChip({
    required this.topic,
    required this.style,
    required this.selected,
    required this.atCap,
    required this.onTap,
  });

  final Topic topic;
  final InterestStyle style;
  final bool selected;
  final bool atCap;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final Color bg = selected ? style.background : NFColors.surface;
    final Color fg = selected ? style.foreground : NFColors.ink2;
    final BoxBorder border = selected
        ? Border.all(color: NFColors.ink, width: 1.5)
        : Border.all(color: NFColors.hairline);

    return Opacity(
      opacity: atCap ? 0.45 : 1.0,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: atCap ? null : onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(999),
            border: border,
            boxShadow: selected
                ? const <BoxShadow>[
                    BoxShadow(
                      color: Color.fromRGBO(0, 0, 0, 0.25),
                      offset: Offset(0, 6),
                      blurRadius: 14,
                      spreadRadius: -8,
                    ),
                  ]
                : null,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text(style.emoji, style: const TextStyle(fontSize: 15)),
              const SizedBox(width: 6),
              Text(
                topic.name,
                style: NFTypography.body.copyWith(
                  fontSize: 14.5,
                  fontWeight: FontWeight.w500,
                  color: fg,
                  height: 1.2,
                ),
              ),
              if (selected) ...[
                const SizedBox(width: 6),
                NFIcon('check', size: 13, color: fg),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _ContinueBar extends StatelessWidget {
  const _ContinueBar({
    required this.enabled,
    required this.saving,
    required this.selectedCount,
    required this.minSelect,
    required this.onContinue,
  });

  final bool enabled;
  final bool saving;
  final int selectedCount;
  final int minSelect;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    final Color bg = enabled ? NFColors.ink : NFColors.surface2;
    final Color fg = enabled ? NFColors.accentInk : NFColors.mute;
    final int missing = (minSelect - selectedCount).clamp(0, minSelect);
    final String label =
        enabled ? 'Продолжить' : 'Выберите ещё $missing';

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: enabled ? onContinue : null,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: BorderRadius.circular(999),
              boxShadow: enabled
                  ? const <BoxShadow>[
                      BoxShadow(
                        color: Color.fromRGBO(0, 0, 0, 0.35),
                        offset: Offset(0, 10),
                        blurRadius: 24,
                        spreadRadius: -10,
                      ),
                    ]
                  : null,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                if (saving)
                  SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(fg),
                    ),
                  )
                else ...[
                  Text(
                    label,
                    style: NFTypography.h2.copyWith(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0,
                      color: fg,
                    ),
                  ),
                  if (enabled) ...[
                    const SizedBox(width: 8),
                    NFIcon('arrow-up-right', size: 15, color: fg),
                  ],
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}
