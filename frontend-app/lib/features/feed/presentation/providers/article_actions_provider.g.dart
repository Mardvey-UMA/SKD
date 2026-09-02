// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'article_actions_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

String _$articleActionsNotifierHash() =>
    r'82638739dda396cb00c96fb5692652fddef145f8';

/// Provides per-article action state derived from the user interaction
/// cache. The notifier no longer holds a private map: instead it asks the
/// cache for any id and exposes a thin family-like API. Mutations call the
/// repository, then update the cache so every consumer (feed, collections,
/// related list, article detail) repaints immediately.
///
/// Copied from [ArticleActionsNotifier].
@ProviderFor(ArticleActionsNotifier)
final articleActionsNotifierProvider =
    AutoDisposeNotifierProvider<
      ArticleActionsNotifier,
      Map<String, ArticleActionState>
    >.internal(
      ArticleActionsNotifier.new,
      name: r'articleActionsNotifierProvider',
      debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
          ? null
          : _$articleActionsNotifierHash,
      dependencies: null,
      allTransitiveDependencies: null,
    );

typedef _$ArticleActionsNotifier =
    AutoDisposeNotifier<Map<String, ArticleActionState>>;
// ignore_for_file: type=lint
// ignore_for_file: subtype_of_sealed_class, invalid_use_of_internal_member, invalid_use_of_visible_for_testing_member, deprecated_member_use_from_same_package
