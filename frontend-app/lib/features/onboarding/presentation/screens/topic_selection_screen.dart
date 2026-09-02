import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../shared/widgets/primary_button.dart';
import '../providers/onboarding_provider.dart';
import '../providers/topics_provider.dart';
import '../providers/selected_topics_provider.dart';
import '../widgets/interest_chip.dart';
import '../widgets/onboarding_progress_bar.dart';

// §5.1 Aurora background — two radial layers + neutral-50 base
class _AuroraBackground extends StatelessWidget {
  const _AuroraBackground({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        const ColoredBox(color: Color(0xFFF5F7FC)),
        Container(
          decoration: const BoxDecoration(
            gradient: RadialGradient(
              center: Alignment(-0.6, -1.2),
              radius: 1.4,
              colors: [Color(0xFFE8EEFF), Colors.transparent],
            ),
          ),
        ),
        Container(
          decoration: const BoxDecoration(
            gradient: RadialGradient(
              center: Alignment(1.0, 1.2),
              radius: 1.2,
              colors: [Color(0xFFFCE8F0), Colors.transparent],
            ),
          ),
        ),
        child,
      ],
    );
  }
}

// Ambient pulse controller — one-shot on enable (duration-ambient = 800ms).
class _PulsingButton extends StatefulWidget {
  const _PulsingButton({
    required this.enabled,
    required this.isLoading,
    required this.onPressed,
  });

  final bool enabled;
  final bool isLoading;
  final VoidCallback? onPressed;

  @override
  State<_PulsingButton> createState() => _PulsingButtonState();
}

class _PulsingButtonState extends State<_PulsingButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse;
  late final Animation<double> _opacity;
  bool _wasPreviouslyEnabled = false;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800), // duration-ambient
    );
    _opacity = TweenSequence<double>([
      TweenSequenceItem(tween: Tween(begin: 1.0, end: 1.4), weight: 50),
      TweenSequenceItem(tween: Tween(begin: 1.4, end: 1.0), weight: 50),
    ]).animate(_pulse);
    _wasPreviouslyEnabled = widget.enabled;
  }

  @override
  void didUpdateWidget(_PulsingButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_wasPreviouslyEnabled && widget.enabled) {
      _pulse.forward(from: 0);
    }
    _wasPreviouslyEnabled = widget.enabled;
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _opacity,
      builder: (context, child) {
        // Animate the shadow glow intensity without touching boxShadow directly
        // (using opacity on a glow overlay layer per §7.4 — we crossfade shadow opacity)
        return AnimatedOpacity(
          opacity: widget.enabled ? 1.0 : 0.4,
          duration: const Duration(milliseconds: 220),
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              boxShadow: widget.enabled
                  ? [
                      BoxShadow(
                        color: Color.fromRGBO(
                          61,
                          91,
                          255,
                          // pulse: interpolate alpha between 0.28 and 0.28*1.4
                          0.28 * (_pulse.isAnimating ? _opacity.value : 1.0),
                        ),
                        offset: const Offset(0, 8),
                        blurRadius: 24,
                      ),
                    ]
                  : [],
            ),
            child: child,
          ),
        );
      },
      child: PrimaryButton(
        text: 'Продолжить',
        onPressed: widget.enabled && !widget.isLoading ? widget.onPressed : null,
        isLoading: widget.isLoading,
        isEnabled: widget.enabled,
      ),
    );
  }
}

class TopicSelectionScreen extends ConsumerStatefulWidget {
  const TopicSelectionScreen({super.key});

  @override
  ConsumerState<TopicSelectionScreen> createState() =>
      _TopicSelectionScreenState();
}

class _TopicSelectionScreenState extends ConsumerState<TopicSelectionScreen> {
  bool _isSaving = false;

  Future<void> _continue(int minSelect, int maxSelect) async {
    final selectedIds = ref.read(selectedTopicsNotifierProvider).toList();

    if (selectedIds.length < minSelect) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Выберите не менее $minSelect тем.')),
      );
      return;
    }
    if (selectedIds.length > maxSelect) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Можно выбрать не более $maxSelect тем.')),
      );
      return;
    }

    setState(() => _isSaving = true);
    try {
      final repo = ref.read(onboardingRepositoryProvider);
      await repo.completeOnboarding(selectedIds, []);
      ref.invalidate(onboardingStatusProvider);
      if (mounted) {
        context.go('/shell/feed');
      }
    } catch (e) {
      if (e.toString().contains('Conflict') ||
          e.toString().contains('409') ||
          e.toString().contains('already')) {
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
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final topicsAsync = ref.watch(topicsNotifierProvider);
    final selectedIds = ref.watch(selectedTopicsNotifierProvider);

    final canContinue = topicsAsync.maybeWhen(
      data: (response) =>
          selectedIds.length >= response.minSelect &&
          selectedIds.length <= response.maxSelect &&
          !_isSaving,
      orElse: () => false,
    );

    final maxSelect = topicsAsync.maybeWhen(
      data: (r) => r.maxSelect,
      orElse: () => 5,
    );

    return Scaffold(
      backgroundColor: Colors.transparent,
      extendBodyBehindAppBar: true,
      bottomNavigationBar: Material(
        type: MaterialType.transparency,
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
            child: _PulsingButton(
              enabled: canContinue,
              isLoading: _isSaving,
              onPressed: topicsAsync.maybeWhen(
                data: (response) => canContinue
                    ? () => _continue(
                          response.minSelect,
                          response.maxSelect,
                        )
                    : null,
                orElse: () => null,
              ),
            ),
          ),
        ),
      ),
      body: _AuroraBackground(
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
            children: [
              // Top row: back arrow + progress bar
              Row(
                children: [
                  SizedBox(
                    width: 44,
                    height: 44,
                    child: GestureDetector(
                      onTap: () => GoRouter.of(context).pop(),
                      child: const Center(
                        child: Icon(
                          Icons.arrow_back,
                          color: Color(0xFF0E1525), // text.primary
                          size: 24,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: OnboardingProgressBar(currentStep: 2, totalSteps: 3),
                  ),
                ],
              ),
              const SizedBox(height: 32),
              // Title: display-l
              const Text(
                'Что вас интересует?',
                style: TextStyle(
                  fontFamily: 'Manrope',
                  fontSize: 28,
                  height: 36 / 28,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.013 * 28,
                  color: Color(0xFF0E1525), // text.primary
                ),
              ),
              const SizedBox(height: 8),
              // Subtitle row with live counter
              topicsAsync.when(
                loading: () => _subtitleRow(
                  'Выберите темы, чтобы настроить ленту.',
                  selectedIds.length,
                  maxSelect,
                ),
                error: (e, st) => _subtitleRow(
                  'Выберите темы, чтобы настроить ленту.',
                  selectedIds.length,
                  maxSelect,
                ),
                data: (response) => _subtitleRow(
                  'Выберите от ${response.minSelect} до ${response.maxSelect} тем.',
                  selectedIds.length,
                  response.maxSelect,
                ),
              ),
              const SizedBox(height: 24),
              // Chips
              topicsAsync.when(
                loading: () => _buildShimmerChips(),
                error: (e, _) => _buildErrorState(),
                data: (response) => Wrap(
                  spacing: 12, // space-sm
                  runSpacing: 12,
                  children: response.categories.map((topic) {
                    return InterestChip(
                      label: topic.name,
                      icon: topic.iconData,
                      selected: selectedIds.contains(topic.id),
                      onTap: () {
                        final notifier = ref.read(
                          selectedTopicsNotifierProvider.notifier,
                        );
                        final alreadySelected = selectedIds.contains(topic.id);
                        if (!alreadySelected &&
                            selectedIds.length >= response.maxSelect) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                'Максимум ${response.maxSelect} тем.',
                              ),
                            ),
                          );
                          return;
                        }
                        notifier.toggle(topic.id);
                      },
                    );
                  }).toList(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _subtitleRow(String text, int count, int maxCount) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              fontSize: 14,
              height: 20 / 14,
              fontWeight: FontWeight.w400,
              color: Color(0xFF485063),
            ),
          ),
        ),
        const SizedBox(width: 8),
        Text(
          'Выбрано: $count/$maxCount',
          style: const TextStyle(
            fontSize: 13,
            height: 18 / 13,
            fontWeight: FontWeight.w400,
            letterSpacing: 0.002 * 13,
            color: Color(0xFF8792A6),
          ),
        ),
      ],
    );
  }

  Widget _buildShimmerChips() {
    return RepaintBoundary(
      child: Wrap(
        spacing: 12,
        runSpacing: 12,
        children: List.generate(6, (_) => const _ShimmerPill()),
      ),
    );
  }

  Widget _buildErrorState() {
    return Center(
      child: Column(
        children: [
          const Text('Не удалось загрузить темы.'),
          TextButton(
            onPressed: () => ref.invalidate(topicsNotifierProvider),
            child: const Text('Повторить'),
          ),
        ],
      ),
    );
  }
}

class _ShimmerPill extends StatefulWidget {
  const _ShimmerPill();

  @override
  State<_ShimmerPill> createState() => _ShimmerPillState();
}

class _ShimmerPillState extends State<_ShimmerPill>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _opacity;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);
    _opacity = Tween<double>(begin: 0.4, end: 1.0).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _opacity,
      child: Container(
        width: 110,
        height: 44,
        decoration: BoxDecoration(
          color: const Color(0xFFEEF1F9), // surface.sunken
          borderRadius: BorderRadius.circular(9999),
        ),
      ),
    );
  }
}
