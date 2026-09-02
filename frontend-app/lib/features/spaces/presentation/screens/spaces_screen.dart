import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/config/feature_flags.dart';
import '../../../../shared/widgets/app_fab.dart';
import '../../../../shared/widgets/icon_tile.dart';
import '../../../../theme/app_tokens.dart';
import '../../../../theme/tokens/app_colors.dart' as ds;
import '../../domain/entities/space.dart';
import '../providers/spaces_providers.dart';
import '../widgets/space_limit_banner.dart';
import '../widgets/space_tile.dart';

class SpacesScreen extends ConsumerWidget {
  const SpacesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(spacesListNotifierProvider);
    final items = state.valueOrNull ?? const <Space>[];
    final atLimit = items.length >= FeatureFlags.maxSpaces;
    final tokens = Theme.of(context).extension<AppTokens>()!;

    void onCreateTap() {
      if (atLimit) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Максимум ${FeatureFlags.maxSpaces} пространств. '
              'Удалите одно, чтобы создать новое.',
            ),
          ),
        );
      } else {
        context.push('/spaces/new');
      }
    }

    return Scaffold(
      backgroundColor: tokens.colors.surface.background,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Header ──────────────────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.md, 8, AppSpacing.lg, 0),
              child: Row(
                children: [
                  SizedBox(
                    width: 44,
                    height: 44,
                    child: IconButton(
                      padding: EdgeInsets.zero,
                      icon: const Icon(Icons.arrow_back_ios_new, size: 24),
                      color: tokens.colors.text.primary,
                      onPressed: () => Navigator.pop(context),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.xs),
                  Text(
                    'Мои пространства',
                    style: tokens.typography.headingL.copyWith(
                      color: tokens.colors.text.primary,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            // ── Body ─────────────────────────────────────────────────────────
            Expanded(
              child: state.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(
                  child: TextButton(
                    onPressed: () => ref.invalidate(spacesListNotifierProvider),
                    child: Text('Ошибка: $e. Повторить?'),
                  ),
                ),
                data: (spaces) {
                  if (spaces.isEmpty) return _EmptyState(onCreateTap: onCreateTap);
                  return RefreshIndicator(
                    onRefresh: () =>
                        ref.read(spacesListNotifierProvider.notifier).refresh(),
                    child: ListView(
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpacing.lg,
                      ),
                      children: [
                        // Card wrapping all rows
                        Container(
                          decoration: BoxDecoration(
                            color: tokens.colors.surface.base,
                            borderRadius: AppRadii.blg,
                            boxShadow: AppElevation.elev1,
                          ),
                          child: Column(
                            children: spaces
                                .map(
                                  (s) => SpaceTile(
                                    space: s,
                                    onTap: () => context.push('/spaces/${s.id}'),
                                    onLongPress: () =>
                                        _showActions(context, ref, s),
                                  ),
                                )
                                .toList(),
                          ),
                        ),
                        if (atLimit) ...[
                          const SizedBox(height: AppSpacing.sm),
                          const SpaceLimitBanner(),
                        ],
                        // FAB clearance
                        const SizedBox(height: 80),
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: Tooltip(
        message: atLimit
            ? 'Максимум ${FeatureFlags.maxSpaces} пространств'
            : 'Создать пространство',
        child: AppFab(
          icon: Icons.add,
          onPressed: onCreateTap,
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  void _showActions(BuildContext context, WidgetRef ref, Space s) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('Изменить'),
              onTap: () {
                Navigator.pop(ctx);
                context.push('/spaces/${s.id}/edit');
              },
            ),
            ListTile(
              leading:
                  Icon(Icons.delete_outline, color: ds.AppColors.error),
              title: Text(
                'Удалить',
                style: TextStyle(color: ds.AppColors.error),
              ),
              onTap: () async {
                Navigator.pop(ctx);
                final messenger = ScaffoldMessenger.of(context);
                final confirmed = await _confirmDelete(context, s.name);
                if (confirmed == true) {
                  try {
                    await ref
                        .read(spacesListNotifierProvider.notifier)
                        .deleteSpace(s.id);
                  } catch (e) {
                    messenger.showSnackBar(
                      SnackBar(content: Text('Не удалось удалить: $e')),
                    );
                  }
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<bool?> _confirmDelete(BuildContext context, String name) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Удалить «$name»?'),
        content: const Text(
          'Источники останутся в каталоге. Пространство будет удалено.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Отмена'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: ds.AppColors.error,
            ),
            child: const Text('Удалить'),
          ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.onCreateTap});

  final VoidCallback onCreateTap;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;

    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl2),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Illustration: accent-sky tinted circle with folder icon
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AccentVariant.sky.bg,
              ),
              child: Icon(
                Icons.folder_outlined,
                size: 36,
                color: AccentVariant.sky.fg,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              'Создайте своё пространство',
              textAlign: TextAlign.center,
              style: tokens.typography.headingS.copyWith(
                color: tokens.colors.text.primary,
              ),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Группируйте источники по темам и настраивайте ленту под себя',
              textAlign: TextAlign.center,
              style: tokens.typography.bodyM.copyWith(
                color: tokens.colors.text.secondary,
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            OutlinedButton.icon(
              onPressed: onCreateTap,
              icon: const Icon(Icons.add),
              label: const Text('Создать'),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size(160, 48),
                shape: const StadiumBorder(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
