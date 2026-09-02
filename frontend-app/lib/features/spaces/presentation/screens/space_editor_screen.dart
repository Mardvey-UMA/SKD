import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../../core/sources/domain/space_color.dart';
import '../../../../theme/app_tokens.dart';
import '../providers/spaces_providers.dart';
import '../widgets/colour_swatch_grid.dart';
import '../widgets/source_multi_select_list.dart';

class SpaceEditorScreen extends ConsumerStatefulWidget {
  const SpaceEditorScreen({super.key, this.existingSpaceId});

  final String? existingSpaceId;

  @override
  ConsumerState<SpaceEditorScreen> createState() =>
      _SpaceEditorScreenState();
}

class _SpaceEditorScreenState extends ConsumerState<SpaceEditorScreen> {
  final _nameController = TextEditingController();
  SpaceColor _color = SpaceColor.blue;
  Set<String> _sourceIds = <String>{};
  String? _nameError;
  bool _saving = false;
  bool _hydrated = false;

  static const int _nameMaxLength = 50;

  bool get _isEdit => widget.existingSpaceId != null;

  @override
  void initState() {
    super.initState();
    _nameController.addListener(_onNameChanged);
  }

  @override
  void dispose() {
    _nameController.removeListener(_onNameChanged);
    _nameController.dispose();
    super.dispose();
  }

  void _onNameChanged() {
    if (_nameError != null) setState(() => _nameError = null);
    setState(() {});
  }

  void _hydrateIfNeeded() {
    if (_hydrated || !_isEdit) return;
    final space = ref.read(spaceByIdProvider(widget.existingSpaceId!));
    if (space != null) {
      _nameController.text = space.name;
      _color = space.color;
      _sourceIds = space.sourceIds.toSet();
      _hydrated = true;
    }
  }

  bool _isValid() {
    return _nameController.text.trim().isNotEmpty && _sourceIds.isNotEmpty;
  }

  Future<void> _save() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      setState(() => _nameError = 'Введите название');
      return;
    }
    if (_sourceIds.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Выберите хотя бы один источник')),
      );
      return;
    }
    setState(() {
      _saving = true;
      _nameError = null;
    });
    final notifier = ref.read(spacesListNotifierProvider.notifier);
    try {
      if (_isEdit) {
        await notifier.updateSpace(
          widget.existingSpaceId!,
          name: name,
          color: _color,
          sourceIds: _sourceIds.toList(),
        );
      } else {
        await notifier.createSpace(
          name: name,
          color: _color,
          sourceIds: _sourceIds.toList(),
        );
      }
      if (!mounted) return;
      context.pop();
    } on DuplicateNameFailure catch (e) {
      setState(() {
        _saving = false;
        _nameError = e.message;
      });
    } on LimitReachedFailure catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.message)),
      );
    } on AppFailure catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.message)),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Ошибка: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    _hydrateIfNeeded();
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final nameLength = _nameController.text.length;

    return Scaffold(
      backgroundColor: tokens.colors.surface.background,
      appBar: AppBar(
        backgroundColor: tokens.colors.surface.background,
        foregroundColor: tokens.colors.text.primary,
        elevation: 0,
        titleSpacing: 0,
        title: Text(
          _isEdit ? 'Изменить пространство' : 'Новое пространство',
          style: tokens.typography.headingM.copyWith(
            color: tokens.colors.text.primary,
          ),
          maxLines: 2,
          overflow: TextOverflow.visible,
        ),
        actions: [
          TextButton(
            onPressed: _saving || !_isValid() ? null : _save,
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.md,
                vertical: AppSpacing.sm,
              ),
              minimumSize: const Size(44, 44),
            ),
            child: _saving
                ? SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: tokens.colors.interactive.primary,
                    ),
                  )
                : Text(
                    'Сохранить',
                    style: tokens.typography.bodyL.copyWith(
                      fontWeight: FontWeight.w600,
                      color: _isValid()
                          ? tokens.colors.interactive.primary
                          : tokens.colors.text.disabled,
                    ),
                  ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.lg,
          vertical: AppSpacing.sm,
        ),
        children: [
          // Name input
          _NameField(
            controller: _nameController,
            maxLength: _nameMaxLength,
            currentLength: nameLength,
            errorText: _nameError,
            tokens: tokens,
          ),
          const SizedBox(height: AppSpacing.xl),

          // Colour section
          _SectionHeader('Цвет', tokens: tokens),
          const SizedBox(height: AppSpacing.sm),
          ColourSwatchGrid(
            selected: _color,
            onChanged: (c) => setState(() => _color = c),
          ),
          const SizedBox(height: AppSpacing.xl),

          // Sources section
          _SectionHeader('Источники', tokens: tokens),
          const SizedBox(height: AppSpacing.sm),
          SourceMultiSelectList(
            selectedIds: _sourceIds,
            onChanged: (v) => setState(() => _sourceIds = v),
          ),
          const SizedBox(height: AppSpacing.xl),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.text, {required this.tokens});

  final String text;
  final AppTokens tokens;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: tokens.typography.bodyS.copyWith(
        color: tokens.colors.text.secondary,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}

class _NameField extends StatefulWidget {
  const _NameField({
    required this.controller,
    required this.maxLength,
    required this.currentLength,
    required this.errorText,
    required this.tokens,
  });

  final TextEditingController controller;
  final int maxLength;
  final int currentLength;
  final String? errorText;
  final AppTokens tokens;

  @override
  State<_NameField> createState() => _NameFieldState();
}

class _NameFieldState extends State<_NameField> {
  final _focusNode = FocusNode();
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(() {
      setState(() => _isFocused = _focusNode.hasFocus);
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  Color get _borderColor {
    if (widget.errorText != null) return AppColors.borderError;
    if (_isFocused) return AppColors.borderFocus;
    return AppColors.borderDefault;
  }

  List<BoxShadow> get _halo {
    if (widget.errorText != null) {
      return [
        BoxShadow(
          color: AppColors.borderErrorHalo,
          spreadRadius: 4,
          blurRadius: 0,
        ),
      ];
    }
    if (_isFocused) {
      return [
        BoxShadow(
          color: AppColors.borderFocusHalo,
          spreadRadius: 4,
          blurRadius: 0,
        ),
      ];
    }
    return const [];
  }

  @override
  Widget build(BuildContext context) {
    final tokens = widget.tokens;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AnimatedContainer(
          duration: AppMotion.fast,
          height: 56,
          decoration: BoxDecoration(
            color: AppColors.surfaceBase,
            borderRadius: AppRadii.bmd,
            border: Border.all(color: _borderColor, width: 1.5),
            boxShadow: _halo,
          ),
          child: TextField(
            controller: widget.controller,
            focusNode: _focusNode,
            maxLength: widget.maxLength,
            style: tokens.typography.bodyL.copyWith(
              color: tokens.colors.text.primary,
            ),
            decoration: InputDecoration(
              labelText: 'Название',
              labelStyle: tokens.typography.bodyM.copyWith(
                color: tokens.colors.text.tertiary,
              ),
              floatingLabelStyle: tokens.typography.caption.copyWith(
                color: _isFocused
                    ? tokens.colors.interactive.primary
                    : tokens.colors.text.secondary,
              ),
              floatingLabelBehavior: FloatingLabelBehavior.auto,
              counterText: '',
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.md,
                vertical: AppSpacing.md,
              ),
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.xs2),
        Row(
          children: [
            if (widget.errorText != null) ...[
              Icon(
                Icons.info_outline,
                size: 14,
                color: tokens.colors.status.error,
              ),
              const SizedBox(width: AppSpacing.xs2),
              Expanded(
                child: Text(
                  widget.errorText!,
                  style: tokens.typography.caption.copyWith(
                    color: tokens.colors.status.error,
                  ),
                ),
              ),
            ] else
              const Spacer(),
            Text(
              '${widget.currentLength}/${widget.maxLength}',
              style: tokens.typography.caption.copyWith(
                color: tokens.colors.text.tertiary,
              ),
            ),
          ],
        ),
      ],
    );
  }
}
