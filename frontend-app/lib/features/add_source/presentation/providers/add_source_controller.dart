import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/errors/app_failures.dart';
import '../../../auth/presentation/providers/dio_provider.dart';
import '../../../sources_catalog/presentation/providers/sources_catalog_provider.dart';
import '../../data/repositories/api_add_source_repository.dart';
import '../../domain/entities/add_source_result.dart';
import '../../domain/repositories/i_add_source_repository.dart';
import '../../domain/value_objects/source_input.dart';

final addSourceRepositoryProvider = Provider<IAddSourceRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return ApiAddSourceRepository(dio);
});

sealed class AddSourceState {
  const AddSourceState(this.input);
  final String input;
}

class AddSourceIdle extends AddSourceState {
  const AddSourceIdle(super.input);
}

class AddSourceDetected extends AddSourceState {
  const AddSourceDetected(super.input, this.parsed);
  final SourceInput parsed;
}

class AddSourceSubmitting extends AddSourceState {
  const AddSourceSubmitting(super.input, this.parsed);
  final SourceInput parsed;
}

class AddSourceSuccess extends AddSourceState {
  const AddSourceSuccess(super.input, this.result);
  final AddSourceResult result;
}

class AddSourceFailed extends AddSourceState {
  const AddSourceFailed(super.input, this.parsed, this.failure);
  final SourceInput? parsed;
  final AppFailure failure;
}

class AddSourceController extends Notifier<AddSourceState> {
  @override
  AddSourceState build() => const AddSourceIdle('');

  void onInputChanged(String raw) {
    final parsed = SourceInput.tryParse(raw);
    state = parsed == null
        ? AddSourceIdle(raw)
        : AddSourceDetected(raw, parsed);
  }

  Future<void> submit() async {
    final current = state;
    final parsed = switch (current) {
      AddSourceDetected(:final parsed) => parsed,
      AddSourceFailed(:final parsed?) => parsed,
      _ => null,
    };
    if (parsed == null) return;

    state = AddSourceSubmitting(current.input, parsed);
    try {
      final result =
          await ref.read(addSourceRepositoryProvider).addSource(
                type: parsed.type,
                params: parsed.params,
              );
      // Invalidate any open catalog query so it refetches with the new source.
      ref.invalidate(sourcesCatalogNotifierProvider);
      state = AddSourceSuccess(current.input, result);
    } on AppFailure catch (e) {
      state = AddSourceFailed(current.input, parsed, e);
    } catch (e) {
      state = AddSourceFailed(
        current.input,
        parsed,
        ServerFailure(e.toString()),
      );
    }
  }

  void reset() {
    state = const AddSourceIdle('');
  }
}

final addSourceControllerProvider =
    NotifierProvider<AddSourceController, AddSourceState>(
  AddSourceController.new,
);
