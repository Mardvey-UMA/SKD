import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/theme/app_colors.dart';
import '../providers/add_source_controller.dart';
import '../widgets/source_preview_card.dart';

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
    if (success.result.wasExisting) {
      _showAlreadyExistsSheet(success);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Источник добавлен')),
      );
      context.pop();
    }
  }

  void _showAlreadyExistsSheet(AddSourceSuccess success) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      backgroundColor: AppColors.surface,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Этот источник уже есть',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              success.result.source.name,
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: () {
                Navigator.of(ctx).pop();
                context.pop();
                context.push('/sources?highlight=${success.result.source.id}');
              },
              child: const Text('Открыть'),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () {
                Navigator.of(ctx).pop();
                context.pop();
              },
              child: const Text('Понятно'),
            ),
          ],
        ),
      ),
    );
  }

  void _onFailure(AddSourceFailed failed) {
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
          actions: [
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

    final isSubmitting = state is AddSourceSubmitting;
    final canSubmit =
        (state is AddSourceDetected) || (state is AddSourceFailed);
    final failure = state is AddSourceFailed ? state.failure : null;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Добавить источник'),
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _controller,
                onChanged: notifier.onInputChanged,
                decoration: const InputDecoration(
                  hintText: 'Ссылка или @username',
                  helperText:
                      't.me/…, habr.com/hub/…, vc.ru/u/… или vc.ru/<blog>',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              if (state is AddSourceDetected)
                SourcePreviewCard(parsed: state.parsed),
              if (state is AddSourceSubmitting)
                SourcePreviewCard(parsed: state.parsed),
              if (state is AddSourceFailed && state.parsed != null)
                SourcePreviewCard(parsed: state.parsed!),
              if (failure != null) ...[
                const SizedBox(height: 12),
                _FailureBanner(failure: failure),
              ],
              const Spacer(),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton(
                  onPressed: canSubmit && !isSubmitting ? notifier.submit : null,
                  child: isSubmitting
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Добавить'),
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: isSubmitting ? null : () => context.pop(),
                child: const Text('Отмена'),
              ),
            ],
          ),
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
        color: const Color(0xFFFEE2E2),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.error.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppColors.error, size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              failure.message,
              style: const TextStyle(
                fontSize: 13,
                color: AppColors.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
