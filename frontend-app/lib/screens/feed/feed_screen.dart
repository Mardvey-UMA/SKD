import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:visibility_detector/visibility_detector.dart';

import 'package:collection/collection.dart';

import '../../core/config/feature_flags.dart';
import '../../core/config/media_config.dart';
import '../../core/sources/domain/source_type.dart';
import '../../features/sources_catalog/domain/entities/source.dart';
import '../../features/sources_catalog/presentation/providers/sources_catalog_provider.dart';
import '../../features/blocked_sources/presentation/providers/blocked_sources_provider.dart';
import '../../features/feed/domain/models/content_item.dart';
import '../../features/feed/domain/models/feed_state.dart';
import '../../features/feed/presentation/controllers/feed_screen_controller.dart';
import '../../features/feed/presentation/providers/article_actions_provider.dart';
import '../../features/feed/presentation/providers/feed_provider.dart';
import '../../features/interactions/domain/models/interaction_event.dart';
import '../../features/interactions/presentation/providers/interaction_service_provider.dart';
import '../../features/subscription/presentation/providers/subscription_provider.dart';
import '../../models/ad.dart';
import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../shared/widgets/app_fab.dart';
import '../../theme/colors.dart';
import '../../ui/atoms/empty_state.dart';
import '../../ui/atoms/feed_skeleton.dart';
import '../../ui/atoms/nf_text.dart';
import '../../ui/atoms/stripe_placeholder.dart';
import '../../ui/cards/ad_card.dart';
import '../../ui/cards/card_item.dart';
import '../../ui/cards/long_card.dart';
import '../../ui/cards/short_card.dart';
import 'feed_header.dart';
import 'widgets/card_menu_launcher.dart';

/// Redesigned Feed screen — renders the sticky [FeedHeader] + a
/// [ShortCard] / [LongCard] / [AdCard] sequence driven by the existing
/// `FeedNotifier`, with the ad-injection / hidden-ad bookkeeping owned by
/// [FeedScreenController].
///
/// Side-effects are preserved 1:1 from the pre-redesign screen:
/// * `FeedNotifier.loadFeed` on first mount.
/// * Pagination via a [ScrollController] that triggers `loadMore` at 70 %
///   scroll extent.
/// * 10-s empty-feed retry timer (matches rec-system warm-up window).
/// * Per-card IMPRESSION / CLOSE emission via [VisibilityDetector]
///   (threshold 0.5, ≥2 s = IMPRESSION, <2 s = CLOSE). OPEN fires on card
///   tap. All carry `feed_request_id`, `position_in_feed`, `device_type`,
///   `app_version` — identical fields to the legacy `FeedCard`.
/// * LIKE / DISLIKE / BOOKMARK go through
///   `ArticleActionsNotifier`, which batches via `InteractionService`.
class FeedScreen extends ConsumerStatefulWidget {
  const FeedScreen({super.key});

  @override
  ConsumerState<FeedScreen> createState() => _FeedScreenState();
}

class _FeedScreenState extends ConsumerState<FeedScreen> {
  late final ScrollController _scrollController;
  Timer? _emptyFeedTimer;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController()..addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(feedNotifierProvider.notifier).loadFeed();
    });
  }

  @override
  void dispose() {
    _emptyFeedTimer?.cancel();
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _scheduleEmptyFeedRetry() {
    _emptyFeedTimer?.cancel();
    _emptyFeedTimer = Timer(const Duration(seconds: 10), () {
      if (mounted) {
        ref.read(feedNotifierProvider.notifier).retryEmpty();
      }
    });
  }

  void _onScroll() {
    final ScrollPosition pos = _scrollController.position;
    if (pos.maxScrollExtent > 0 && pos.pixels >= pos.maxScrollExtent * 0.7) {
      ref.read(feedNotifierProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final FeedState feed = ref.watch(feedNotifierProvider);
    final FeedScreenState screen = ref.watch(feedScreenControllerProvider);
    final bool isPremium =
        ref.watch(subscriptionNotifierProvider).valueOrNull?.isPremium ?? false;
    final Breakpoint bp = context.breakpoint;

    final bool isLoading =
        feed.items.isEmpty && !feed.isRefreshing && !feed.isLoaded;
    final bool isEmpty = feed.items.isEmpty && feed.isLoaded;

    if (isEmpty) {
      _scheduleEmptyFeedRetry();
    }

    final List<FeedEntry> entries = isLoading || isEmpty
        ? const <FeedEntry>[]
        : buildFeedEntries(
            items: feed.items,
            hiddenAds: screen.hiddenAds,
            adFrequency: screen.adFrequency,
          );

    return Scaffold(
      backgroundColor: NFColors.bg,
      floatingActionButton: isPremium
          ? AppFab(
              icon: Icons.dashboard_customize_outlined,
              onPressed: () => context.push('/spaces'),
            )
          : null,
      body: RefreshIndicator(
        onRefresh: () => ref.read(feedNotifierProvider.notifier).refresh(),
        child: CustomScrollView(
          controller: _scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: <Widget>[
            // Header as a plain box adapter — SliverPersistentHeader with our
            // delegate produced `SliverGeometry is not valid: layoutExtent
            // exceeds paintExtent` on first paint after state flipped to
            // loaded=true, which cascaded into a `null` check in
            // `viewport._paintContents` and suppressed every subsequent
            // Sliver (the feed cards). Plain adapter renders reliably and
            // still scrolls with the list.
            SliverToBoxAdapter(
              child: bp == Breakpoint.desktop
                  ? Align(
                      alignment: Alignment.topCenter,
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 760),
                        child: const FeedHeader(),
                      ),
                    )
                  : const FeedHeader(),
            ),
            if (isLoading)
              SliverToBoxAdapter(
                child: Padding(
                  padding: _listPaddingFor(bp),
                  child: const FeedSkeleton(),
                ),
              )
            else if (isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: _listPaddingFor(bp),
                  child: const EmptyState(
                    iconName: 'radar',
                    accent: EmptyStateAccent.accent,
                    title: 'Пока пусто',
                    desc:
                        'Ваша лента формируется. Мы подбираем свежие материалы '
                        'по вашим интересам — вернитесь через минуту.',
                  ),
                ),
              )
            else
              SliverPadding(
                padding: _listPaddingFor(bp),
                sliver: SliverList.separated(
                  itemCount: entries.length + 2, // + footer + load-indicator
                  separatorBuilder: (_, int i) =>
                      i < entries.length - 1 || i == entries.length
                          ? const SizedBox(height: 14)
                          : const SizedBox.shrink(),
                  itemBuilder: (BuildContext context, int index) {
                    if (index == entries.length) {
                      return _LoadMoreIndicator(
                        isLoadingMore: feed.isLoadingMore,
                        hasNext: feed.hasNext,
                      );
                    }
                    if (index == entries.length + 1) {
                      return const Padding(
                        padding: EdgeInsets.fromLTRB(0, 6, 0, 0),
                        child: Center(
                          child: NFText.mono(
                            '◦ КОНЕЦ ЛЕНТЫ · ПОТЯНИТЕ ДЛЯ ОБНОВЛЕНИЯ ◦',
                          ),
                        ),
                      );
                    }
                    final FeedEntry entry = entries[index];
                    return switch (entry) {
                      FeedAdEntry(:final Ad ad, :final String key) => RepaintBoundary(
                          key: ValueKey<String>(key),
                          child: AdCard(
                            ad: ad,
                            onHide: (Ad a) => ref
                                .read(feedScreenControllerProvider.notifier)
                                .hideAd(a.brand),
                          ),
                        ),
                      FeedItemEntry(
                        :final ContentItem item,
                        :final int positionInFeed,
                      ) =>
                        RepaintBoundary(
                          key: ValueKey<String>('item-${item.id}'),
                          child: _FeedItemView(
                            item: item,
                            positionInFeed: positionInFeed,
                            isPremium: isPremium,
                          ),
                        ),
                    };
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}

EdgeInsets _listPaddingFor(Breakpoint bp) {
  switch (bp) {
    case Breakpoint.mobile:
      // 120 px bottom clears the floating BottomNav pill.
      return const EdgeInsets.fromLTRB(14, 4, 14, 120);
    case Breakpoint.tablet:
      return const EdgeInsets.fromLTRB(32, 4, 32, 120);
    case Breakpoint.desktop:
      // Desktop has SideNav, not BottomNav — no reserved bottom gutter.
      return const EdgeInsets.fromLTRB(24, 4, 24, 32);
  }
}

class _LoadMoreIndicator extends StatelessWidget {
  const _LoadMoreIndicator({
    required this.isLoadingMore,
    required this.hasNext,
  });

  final bool isLoadingMore;
  final bool hasNext;

  @override
  Widget build(BuildContext context) {
    if (isLoadingMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: Center(
          child: SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
    }
    // When there's no next page, the mono footer below already communicates
    // end-of-feed — we emit an empty sliver item here to preserve separator
    // rhythm.
    return hasNext
        ? const SizedBox(height: 8)
        : const SizedBox(height: 4);
  }
}

/// Renders a single [ContentItem] as either a [ShortCard] or [LongCard].
///
/// Owns the per-card [VisibilityDetector] used for IMPRESSION / CLOSE
/// emission — semantics copied verbatim from the legacy `FeedCard` so
/// data-capture invariants (`feed_request_id`, `position_in_feed`,
/// `device_type`, `app_version`) remain identical.
class _FeedItemView extends ConsumerStatefulWidget {
  const _FeedItemView({
    required this.item,
    required this.positionInFeed,
    required this.isPremium,
  });

  final ContentItem item;

  /// 1-indexed position (`position_in_feed`) among real content items.
  final int positionInFeed;

  final bool isPremium;

  @override
  ConsumerState<_FeedItemView> createState() => _FeedItemViewState();
}

class _FeedItemViewState extends ConsumerState<_FeedItemView> {
  DateTime? _visibleSince;
  bool _eventSent = false;

  String? _resolveDeviceType() {
    if (kIsWeb) return 'web';
    if (Platform.isAndroid) return 'android';
    return null;
  }

  void _onVisibilityChanged(VisibilityInfo info) {
    if (!mounted) return;
    if (info.visibleFraction > 0.5) {
      _visibleSince ??= DateTime.now();
      return;
    }
    final DateTime? since = _visibleSince;
    if (since != null && !_eventSent) {
      final double durationSec =
          DateTime.now().difference(since).inMilliseconds / 1000.0;
      final service = ref.read(interactionServiceProvider);
      final String? feedRequestId = ref.read(
        feedNotifierProvider.select((FeedState s) => s.feedRequestId),
      );
      final String? appVersion =
          ref.read(packageInfoProvider).valueOrNull?.version;
      final InteractionAction action = durationSec >= 2.0
          ? InteractionAction.impression
          : InteractionAction.close;
      service.trackEvent(
        InteractionEvent(
          contentId: widget.item.id,
          action: action,
          durationSec: durationSec,
          timestamp: since,
          feedRequestId: feedRequestId,
          positionInFeed: widget.positionInFeed,
          deviceType: _resolveDeviceType(),
          appVersion: appVersion,
        ),
      );
      _eventSent = true;
    }
    _visibleSince = null;
  }

  void _onOpen() {
    final String? feedRequestId = ref.read(
      feedNotifierProvider.select((FeedState s) => s.feedRequestId),
    );
    final String? appVersion =
        ref.read(packageInfoProvider).valueOrNull?.version;
    ref.read(interactionServiceProvider).trackEvent(
          InteractionEvent(
            contentId: widget.item.id,
            action: InteractionAction.open,
            timestamp: DateTime.now(),
            feedRequestId: feedRequestId,
            positionInFeed: widget.positionInFeed,
            deviceType: _resolveDeviceType(),
            appVersion: appVersion,
          ),
        );
    context.push('/shell/feed/${widget.item.id}');
  }

  void _onReact(CardItem _, ReactionKind kind) {
    final notifier = ref.read(articleActionsNotifierProvider.notifier);
    switch (kind) {
      case ReactionKind.like:
        notifier.like(widget.item.id);
      case ReactionKind.dislike:
        notifier.dislike(widget.item.id);
      case ReactionKind.bookmark:
        notifier.toggleSave(widget.item.id);
    }
  }

  Future<void> _onMore() async {
    final ContentItem item = widget.item;
    final String? sourceId = item.sourceId;
    final String sourceTitle = item.sourceName?.isNotEmpty == true
        ? item.sourceName!
        : item.authorName?.isNotEmpty == true
            ? item.authorName!
            : 'Источник';
    final String sourceHandle = item.authorName?.startsWith('@') == true
        ? item.authorName!
        : sourceId != null
            ? '@$sourceId'
            : '@source';

    await openCardMenu(
      context,
      sourceTitle: sourceTitle,
      sourceHandle: sourceHandle,
      isPremium: widget.isPremium,
      onAddToSpace: () {
        if (!mounted) return;
        context.push('/spaces/new');
      },
      onHideSource: () async {
        await _hideSource(item, sourceId);
      },
    );
  }

  Future<void> _hideSource(ContentItem item, String? sourceId) async {
    if (!mounted) return;
    final ScaffoldMessengerState messenger = ScaffoldMessenger.of(context);

    SnackBar floatingSnack(String text) => SnackBar(
          content: Text(text),
          behavior: SnackBarBehavior.floating,
          // Lift the snackbar above the floating bottom nav (12 px from
          // the bottom, nav is ~52 px tall + 24 px visual breathing room).
          margin: const EdgeInsets.fromLTRB(16, 0, 16, 92),
        );

    final SourceType? type = SourceType.fromWire(item.sourceType);
    final String displayName = item.sourceName?.isNotEmpty == true
        ? item.sourceName!
        : item.authorName ?? type?.shortLabel ?? 'Источник';

    // Resolve the source UUID. Backend ships `source_id=null` for
    // Telegram items; fall back to looking the handle up in the
    // public catalog. The catalog stores the channel *handle* as
    // `name` (`mash`, `mskint`, …) — NOT the display title, so we
    // must extract the handle from the item's `url`
    // (e.g. `https://t.me/mskint/12345` → `mskint`) before querying.
    String? resolvedId = sourceId;
    if (resolvedId == null) {
      final String? handle = _extractSourceHandle(item.url);
      final String queryName =
          handle ?? (item.authorName ?? item.sourceName ?? displayName);
      try {
        final catalogRepo = ref.read(sourcesCatalogRepositoryProvider);
        final page = await catalogRepo.fetchCatalog(
          type: type,
          q: queryName,
          limit: 10,
        );
        final List<Source> results = page.items;
        final Source? exact = results.firstWhereOrNull(
          (s) => s.name.toLowerCase() == queryName.toLowerCase(),
        );
        resolvedId = (exact ?? results.firstOrNull)?.id;
      } catch (_) {
        // fall through
      }
    }

    if (resolvedId == null) {
      messenger.showSnackBar(
        floatingSnack('Не удалось найти источник в каталоге'),
      );
      return;
    }

    try {
      if (!FeatureFlags.requireSubscriptionForHideSource || widget.isPremium) {
        await ref
            .read(blockedSourcesNotifierProvider.notifier)
            .block(resolvedId, cachedName: displayName, cachedType: type);
        await ref.read(feedNotifierProvider.notifier).refresh();
        if (!mounted) return;
        messenger.showSnackBar(floatingSnack('Источник скрыт: $displayName'));
      }
    } catch (e) {
      messenger.showSnackBar(floatingSnack('Не удалось скрыть: $e'));
    }
  }

  @override
  Widget build(BuildContext context) {
    final ArticleActionState actionState = ref.watch(
      articleActionsNotifierProvider.select(
        (Map<String, ArticleActionState> map) =>
            map[widget.item.id] ?? const ArticleActionState(),
      ),
    );

    final CardItem cardItem = _toCardItem(widget.item);
    final bool isLong = _isLongForm(widget.item);

    return VisibilityDetector(
      key: ValueKey<String>('feed-card-${widget.item.id}'),
      onVisibilityChanged: _onVisibilityChanged,
      child: isLong
          ? LongCard(
              item: cardItem,
              onOpen: (_) => _onOpen(),
              onReact: _onReact,
              isLiked: actionState.isLiked,
              isDisliked: actionState.isDisliked,
              isBookmarked: actionState.isSaved,
              onMore: _onMore,
            )
          : ShortCard(
              item: cardItem,
              onOpen: (_) => _onOpen(),
              onReact: _onReact,
              isLiked: actionState.isLiked,
              isDisliked: actionState.isDisliked,
              isBookmarked: actionState.isSaved,
              onMore: _onMore,
            ),
    );
  }
}

/// Pulls the channel handle out of a source URL. Examples:
///   https://t.me/mskint/12345 → `mskint`
///   https://habr.com/ru/users/foo → `foo`
///   https://vc.ru/u/12345-nick  → `12345-nick`
/// Returns `null` when the URL doesn't look like a known source shape.
String? _extractSourceHandle(String? url) {
  if (url == null || url.isEmpty) return null;
  final uri = Uri.tryParse(url);
  if (uri == null) return null;
  final List<String> segs = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  if (segs.isEmpty) return null;

  final String host = uri.host.toLowerCase();
  if (host.contains('t.me') || host.contains('telegram')) {
    // https://t.me/{handle}/{msg?} → first segment.
    return segs.first;
  }
  if (host.contains('habr.com')) {
    // .../users/{handle} or .../hub/{slug}/articles/... → second after marker.
    for (final marker in <String>['users', 'hubs', 'companies']) {
      final i = segs.indexOf(marker);
      if (i >= 0 && i + 1 < segs.length) return segs[i + 1];
    }
  }
  if (host.contains('vc.ru')) {
    final i = segs.indexOf('u');
    if (i >= 0 && i + 1 < segs.length) return segs[i + 1];
  }
  return null;
}

bool _isLongForm(ContentItem item) {
  final int? minutes = item.metadata?.readTimeMinutes;
  if (minutes != null && minutes >= 8) return true;
  final String? preview = item.bestPreview;
  return preview != null && preview.length >= 220;
}

CardItem _toCardItem(ContentItem item) {
  final bool firstIsVideo =
      item.media.isNotEmpty && item.media.first.type.toLowerCase() == 'video';
  final String? videoUrl = firstIsVideo
      ? MediaConfig.resolve(item.media.first.url)
      : null;
  final List<String> resolvedImageUrls = item.media
      .where((m) => m.type.toLowerCase() == 'image' || m.type.isEmpty)
      .map((m) => MediaConfig.resolve(m.url))
      .whereType<String>()
      .toList(growable: false);
  final CardImages images = firstIsVideo
      ? CardImages.one // show a placeholder block for the video
      : (resolvedImageUrls.isEmpty
          ? CardImages.none
          : (resolvedImageUrls.length > 1 ? CardImages.multi : CardImages.one));
  final StripeTone tone = _toneFor(item);
  final int seed = item.id.hashCode & 0x7fffffff;
  return CardItem(
    id: item.id,
    source: item.sourceName?.isNotEmpty == true
        ? item.sourceName!
        : item.authorName ?? _fallbackSource(item.sourceType),
    time: item.publishedAt ?? DateTime.now(),
    readTime: item.metadata?.readTimeMinutes != null
        ? '${item.metadata!.readTimeMinutes} мин чтения'
        : null,
    title: item.displayTitle,
    snippet: item.bestPreview ?? '',
    images: images,
    tone: tone,
    seed: seed,
    imageUrl: resolvedImageUrls.isNotEmpty ? resolvedImageUrls.first : null,
    // Cap at 10 for display — MultiImage handles 2/3/4+ layouts; beyond
    // 10 the extras contribute only to the «+N» overlay count.
    imageUrls: resolvedImageUrls.take(10).toList(growable: false),
    isVideo: firstIsVideo,
    videoUrl: videoUrl,
  );
}

String _fallbackSource(String? sourceType) {
  final SourceType? t = SourceType.fromWire(sourceType);
  return t?.shortLabel ?? 'Источник';
}

StripeTone _toneFor(ContentItem item) {
  const List<StripeTone> palette = <StripeTone>[
    StripeTone.ink,
    StripeTone.accent,
    StripeTone.lime,
    StripeTone.rose,
    StripeTone.violet,
    StripeTone.teal,
  ];
  return palette[item.id.hashCode.abs() % palette.length];
}
