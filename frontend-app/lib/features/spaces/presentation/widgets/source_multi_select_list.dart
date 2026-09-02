import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/sources/domain/source_type.dart';
import '../../../../core/sources/presentation/widgets/source_type_badge.dart';
import '../../../../features/sources_catalog/domain/value_objects/catalog_query.dart';
import '../../../../features/sources_catalog/presentation/providers/sources_catalog_provider.dart';
import '../../../../shared/widgets/search_field.dart';
import '../../../../shared/widgets/source_checkbox.dart';
import '../../../../shared/widgets/source_filter_chips.dart';
import '../../../../theme/app_tokens.dart';

class SourceMultiSelectList extends ConsumerStatefulWidget {
  const SourceMultiSelectList({
    super.key,
    required this.selectedIds,
    required this.onChanged,
  });

  final Set<String> selectedIds;
  final ValueChanged<Set<String>> onChanged;

  @override
  ConsumerState<SourceMultiSelectList> createState() =>
      _SourceMultiSelectListState();
}

class _SourceMultiSelectListState extends ConsumerState<SourceMultiSelectList> {
  SourceType? _type;
  String _q = '';
  late final TextEditingController _searchController;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      setState(() => _q = value.trim());
    });
  }

  @override
  Widget build(BuildContext context) {
    final query = CatalogQuery(type: _type, q: _q);
    final state = ref.watch(sourcesCatalogNotifierProvider(query));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SearchField(
          controller: _searchController,
          onChanged: _onSearchChanged,
          placeholder: 'Поиск по названию',
        ),
        const SizedBox(height: AppSpacing.sm),
        SourceFilterChips(
          selected: _type,
          onChanged: (t) => setState(() => _type = t),
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Выбрано: ${widget.selectedIds.length}',
          style: AppTypography.caption.copyWith(
            color: AppColors.textTertiary,
          ),
        ),
        const SizedBox(height: AppSpacing.xs2),
        SizedBox(
          height: 320,
          child: state.when(
            loading: () =>
                const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(child: Text(e.toString())),
            data: (value) {
              if (value.items.isEmpty) {
                return Center(
                  child: Text(
                    'Ничего не найдено',
                    style: AppTypography.bodyM.copyWith(
                      color: AppColors.textTertiary,
                    ),
                  ),
                );
              }
              return ListView.builder(
                itemCount: value.items.length,
                itemBuilder: (_, i) {
                  final source = value.items[i];
                  final isSelected =
                      widget.selectedIds.contains(source.id);
                  return _SourceRow(
                    name: source.name,
                    type: source.type,
                    isSelected: isSelected,
                    onChanged: (v) {
                      final updated = Set<String>.from(widget.selectedIds);
                      if (v) {
                        updated.add(source.id);
                      } else {
                        updated.remove(source.id);
                      }
                      widget.onChanged(updated);
                    },
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }
}

class _SourceRow extends StatelessWidget {
  const _SourceRow({
    required this.name,
    required this.type,
    required this.isSelected,
    required this.onChanged,
  });

  final String name;
  final SourceType type;
  final bool isSelected;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 56,
      child: Row(
        children: [
          SourceCheckbox(value: isSelected, onChanged: onChanged),
          const SizedBox(width: AppSpacing.sm),
          SourceTypeBadge(type: type),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: AppTypography.bodyL.copyWith(
                color: AppColors.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
