// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_interaction_cache_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

String _$userInteractionCacheNotifierHash() =>
    r'3bac6dda75e212fe26f19588c01d30bf3d809474';

/// Lightweight cache of every content id the current user has liked /
/// disliked / bookmarked. Used as a single source of truth for action-bar
/// icon state across feed, collections, related list and article detail.
///
/// Loaded once on authentication by paginating through every collection
/// endpoint until [PaginatedContent.hasNext] is false. Mutations from the
/// action notifier (`like` / `dislike` / `toggleSave`) update the cache
/// optimistically so listeners (icons) repaint instantly.
///
/// Copied from [UserInteractionCacheNotifier].
@ProviderFor(UserInteractionCacheNotifier)
final userInteractionCacheNotifierProvider =
    NotifierProvider<
      UserInteractionCacheNotifier,
      UserInteractionCache
    >.internal(
      UserInteractionCacheNotifier.new,
      name: r'userInteractionCacheNotifierProvider',
      debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
          ? null
          : _$userInteractionCacheNotifierHash,
      dependencies: null,
      allTransitiveDependencies: null,
    );

typedef _$UserInteractionCacheNotifier = Notifier<UserInteractionCache>;
// ignore_for_file: type=lint
// ignore_for_file: subtype_of_sealed_class, invalid_use_of_internal_member, invalid_use_of_visible_for_testing_member, deprecated_member_use_from_same_package
