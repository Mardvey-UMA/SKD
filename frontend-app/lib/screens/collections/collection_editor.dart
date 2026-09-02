import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/errors/app_failures.dart';
import '../../features/spaces/presentation/controllers/collection_tone_mapping.dart';
import '../../features/spaces/presentation/providers/spaces_providers.dart';
import '../../features/spaces/presentation/widgets/source_multi_select_list.dart';
import '../../theme/colors.dart';
import '../../ui/atoms/hatched_painter.dart';
import '../../ui/atoms/nf_input.dart';
import '../../ui/atoms/nf_text.dart';
import '../../ui/atoms/stripe_placeholder.dart';
import '../../ui/atoms/tone_swatch.dart';

/// Collection editor — create / edit the user-owned «пространство».
///
/// This is the redesigned view-layer for the pre-existing
/// `SpaceEditorScreen`. It re-uses the same providers / repository — no
/// domain or network changes.
class CollectionEditorScreen extends ConsumerStatefulWidget {
  const CollectionEditorScreen({super.key, this.existingSpaceId});

  final String? existingSpaceId;

  @override
  ConsumerState<CollectionEditorScreen> createState() =>
      _CollectionEditorScreenState();
}

class _CollectionEditorScreenState
    extends ConsumerState<CollectionEditorScreen> {
  static const int _titleMaxLength = 50;

  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _descController = TextEditingController();
  StripeTone _tone = StripeTone.accent;
  Set<String> _sourceIds = <String>{};
  bool _saving = false;
  bool _hydrated = false;

  bool get _isEdit => widget.existingSpaceId != null;

  @override
  void initState() {
    super.initState();
    _titleController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descController.dispose();
    super.dispose();
  }

  void _hydrateIfNeeded() {
    if (_hydrated || !_isEdit) return;
    final space = ref.read(spaceByIdProvider(widget.existingSpaceId!));
    if (space != null) {
      _titleController.text = space.name;
      _tone = CollectionToneMapping.toneFor(space.color);
      _sourceIds = space.sourceIds.toSet();
      _hydrated = true;
    }
  }

  bool get _canSave => _titleController.text.trim().isNotEmpty;

  Future<void> _save() async {
    final String name = _titleController.text.trim();
    if (name.isEmpty) return;
    if (_sourceIds.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Выберите хотя бы один источник')),
      );
      return;
    }
    setState(() => _saving = true);
    final notifier = ref.read(spacesListNotifierProvider.notifier);
    try {
      if (_isEdit) {
        await notifier.updateSpace(
          widget.existingSpaceId!,
          name: name,
          color: CollectionToneMapping.colorFor(_tone),
          sourceIds: _sourceIds.toList(),
        );
      } else {
        await notifier.createSpace(
          name: name,
          color: CollectionToneMapping.colorFor(_tone),
          sourceIds: _sourceIds.toList(),
        );
      }
      if (!mounted) return;
      context.pop();
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

  Future<void> _delete() async {
    final id = widget.existingSpaceId;
    if (id == null) return;
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Удалить пространство?'),
        content: const Text(
          'Источники останутся в каталоге. Пространство будет удалено.',
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Отмена'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Удалить',
              style: TextStyle(color: NFColors.warn),
            ),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    try {
      await ref.read(spacesListNotifierProvider.notifier).deleteSpace(id);
      router.pop();
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Не удалось: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    _hydrateIfNeeded();

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        child: Column(
          children: <Widget>[
            _EditorHeader(
              title: _isEdit ? 'Редактировать' : 'Новое пространство',
              canSave: _canSave && !_saving,
              isSaving: _saving,
              onBack: () => context.pop(),
              onSave: _save,
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(14, 14, 14, 40),
                children: <Widget>[
                  _PreviewTile(
                    tone: _tone,
                    title: _titleController.text.trim().isEmpty
                        ? 'Новое пространство'
                        : _titleController.text.trim(),
                  ),
                  const SizedBox(height: 16),

                  _LabelledField(
                    label: 'НАЗВАНИЕ',
                    child: NFInput(
                      controller: _titleController,
                      placeholder: 'Например, Выходные чтения',
                      maxLength: _titleMaxLength,
                      showCounter: true,
                    ),
                  ),
                  const SizedBox(height: 16),

                  _LabelledField(
                    label: 'ОПИСАНИЕ',
                    child: NFInput(
                      controller: _descController,
                      placeholder: 'Короткая заметка…',
                    ),
                  ),
                  const SizedBox(height: 16),

                  _LabelledField(
                    label: 'ЦВЕТ',
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: NFColors.surface,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: NFColors.hairline),
                      ),
                      child: Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: <Widget>[
                          for (final StripeTone t in collectionPaletteTones)
                            ToneSwatch(
                              tone: t,
                              isSelected: _tone == t,
                              onTap: () => setState(() => _tone = t),
                            ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  _LabelledField(
                    label: 'ИСТОЧНИКИ · ВЫБРАНО ${_sourceIds.length}',
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: NFColors.surface,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: NFColors.hairline),
                      ),
                      child: SourceMultiSelectList(
                        selectedIds: _sourceIds,
                        onChanged: (Set<String> ids) =>
                            setState(() => _sourceIds = ids),
                      ),
                    ),
                  ),

                  if (_isEdit) ...<Widget>[
                    const SizedBox(height: 20),
                    _DeleteActionRow(onTap: _delete),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EditorHeader extends StatelessWidget {
  const _EditorHeader({
    required this.title,
    required this.canSave,
    required this.isSaving,
    required this.onBack,
    required this.onSave,
  });

  final String title;
  final bool canSave;
  final bool isSaving;
  final VoidCallback onBack;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 8, 14, 10),
      child: Row(
        children: <Widget>[
          GestureDetector(
            onTap: onBack,
            child: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: NFColors.surface,
                border: Border.all(color: NFColors.hairline),
              ),
              child: const Icon(
                Icons.arrow_back_ios_new,
                size: 16,
                color: NFColors.ink,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                fontFamily: 'Nunito',
                fontSize: 17,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.3,
                color: NFColors.ink,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          GestureDetector(
            onTap: canSave ? onSave : null,
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: 14,
                vertical: 8,
              ),
              decoration: BoxDecoration(
                color: canSave ? NFColors.ink : Colors.transparent,
                border: Border.all(
                  color: canSave ? NFColors.ink : NFColors.hairline,
                ),
                borderRadius: BorderRadius.circular(999),
              ),
              child: isSaving
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: NFColors.accentInk,
                      ),
                    )
                  : Text(
                      'Сохранить',
                      style: TextStyle(
                        fontFamily: 'Nunito',
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: canSave ? NFColors.accentInk : NFColors.mute,
                      ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LabelledField extends StatelessWidget {
  const _LabelledField({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 6),
          child: NFText.mono(label),
        ),
        child,
      ],
    );
  }
}

class _PreviewTile extends StatelessWidget {
  const _PreviewTile({required this.tone, required this.title});

  final StripeTone tone;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: NFColors.hairline),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: SizedBox(
          height: 100,
          child: Stack(
            fit: StackFit.expand,
            children: <Widget>[
              ColoredBox(color: tone.primary),
              CustomPaint(
                painter: HatchedPainter(
                  period: 14,
                  strokeWidth: 2,
                  color: Color.fromRGBO(14, 15, 13, 0.18),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(14),
                child: Align(
                  alignment: Alignment.bottomLeft,
                  child: Text(
                    title.toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: 'Nunito',
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.6,
                      color: _isDark(tone)
                          ? NFColors.accentInk
                          : NFColors.ink,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static bool _isDark(StripeTone t) =>
      t == StripeTone.ink ||
      t == StripeTone.accent ||
      t == StripeTone.violet;
}

class _DeleteActionRow extends StatelessWidget {
  const _DeleteActionRow({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
        decoration: BoxDecoration(
          color: NFColors.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: NFColors.hairline),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: const <Widget>[
            Icon(Icons.delete_outline, size: 16, color: NFColors.warn),
            SizedBox(width: 8),
            Text(
              'Удалить пространство',
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: NFColors.warn,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
