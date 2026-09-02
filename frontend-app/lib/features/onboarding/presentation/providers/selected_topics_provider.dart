import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'selected_topics_provider.g.dart';

@riverpod
class SelectedTopicsNotifier extends _$SelectedTopicsNotifier {
  @override
  Set<String> build() => {};

  void toggle(String topicId) {
    if (state.contains(topicId)) {
      state = {...state}..remove(topicId);
    } else {
      state = {...state, topicId};
    }
  }

  void clear() => state = {};
}
