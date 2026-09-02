import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../providers/blocked_sources_provider.dart';
import '../widgets/blocked_source_tile.dart';

class BlockedSourcesScreen extends ConsumerWidget {
  const BlockedSourcesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(blockedSourcesNotifierProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Скрытые источники'),
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
      ),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline,
                  size: 48, color: AppColors.error),
              const SizedBox(height: 8),
              Text(
                e.toString(),
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () =>
                    ref.invalidate(blockedSourcesNotifierProvider),
                child: const Text('Повторить'),
              ),
            ],
          ),
        ),
        data: (items) {
          if (items.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.visibility_off_outlined,
                        size: 48, color: AppColors.textHint),
                    SizedBox(height: 12),
                    Text(
                      'Нет скрытых источников',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'Скрыть источник можно из 3-точечного меню на карточке',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 13,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref
                .read(blockedSourcesNotifierProvider.notifier)
                .refresh(),
            child: ListView.separated(
              itemCount: items.length,
              separatorBuilder: (_, __) => const Divider(
                height: 1,
                color: AppColors.surfaceLight,
                indent: 16,
              ),
              itemBuilder: (_, i) {
                final s = items[i];
                return BlockedSourceTile(
                  source: s,
                  onUnblock: () async {
                    final messenger = ScaffoldMessenger.of(context);
                    try {
                      await ref
                          .read(blockedSourcesNotifierProvider.notifier)
                          .unblock(s.sourceId);
                      messenger.showSnackBar(
                        const SnackBar(
                          content: Text('Источник снова отображается'),
                        ),
                      );
                    } catch (e) {
                      messenger.showSnackBar(
                        SnackBar(
                          content: Text('Не удалось: $e'),
                        ),
                      );
                    }
                  },
                );
              },
            ),
          );
        },
      ),
    );
  }
}
