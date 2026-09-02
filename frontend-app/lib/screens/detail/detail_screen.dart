import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_widget_from_html_core/flutter_widget_from_html_core.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';

import '../../core/config/media_config.dart';
import '../../features/feed/domain/models/content_item.dart';
import '../../features/feed/presentation/controllers/detail_screen_controller.dart';
import '../../features/feed/presentation/providers/article_actions_provider.dart';
import '../../features/feed/presentation/providers/feed_provider.dart';
import '../../features/interactions/data/services/interaction_service.dart';
import '../../features/interactions/domain/models/interaction_event.dart';
import '../../features/interactions/presentation/providers/interaction_service_provider.dart';
import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../theme/colors.dart';
import '../../theme/radii.dart';
import '../../ui/atoms/nf_icon.dart';
import '../../ui/atoms/reaction_bar.dart';
import '../../ui/atoms/source_line.dart';
import '../../ui/atoms/stripe_placeholder.dart';
import '../../ui/atoms/video_play_overlay.dart';
import '../../ui/media/media_viewer.dart';
import '../../ui/motion/feed_hero.dart';
import 'related_rail.dart';

/// Redesigned article detail screen — mirrors `DetailScreen` in
/// `design/reference/mockup/screens.jsx` (neo-futurism).
///
/// Behaviour preserved 1:1 from the legacy `ArticleDetailScreen`:
/// * Loads a single [ContentItem] via [contentItemProvider].
/// * Emits a `close` [InteractionEvent] on dispose with `durationSec`
///   (time on screen) and `scrollDepth` (0.0–1.0 fraction of article
///   scrolled) so rec-system `_classify_close()` can bucket full-read /
///   half-read / bounce engagement.
/// * URL-launcher flow and `flutter_widget_from_html_core` rendering are
///   reused verbatim when the article payload is HTML.
class DetailScreen extends ConsumerStatefulWidget {
  const DetailScreen({super.key, required this.articleId});

  final String articleId;

  @override
  ConsumerState<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends ConsumerState<DetailScreen> {
  late final DateTime _openedAt;
  late final InteractionService _interactionService;
  final ScrollController _scrollController = ScrollController();

  /// Last-known scroll progress in [0.0, 1.0]. Tracked via a listener
  /// because by the time [dispose] runs, the child [SingleChildScrollView]
  /// has already detached its controller (child widgets dispose first),
  /// so `_scrollController.hasClients` is false.
  double? _lastScrollDepth;

  @override
  void initState() {
    super.initState();
    _openedAt = DateTime.now();
    _scrollController.addListener(_onScroll);
  }

  void _onScroll() {
    if (!_scrollController.hasClients) return;
    final ScrollPosition pos = _scrollController.position;
    if (pos.maxScrollExtent > 0) {
      _lastScrollDepth = (pos.pixels / pos.maxScrollExtent).clamp(0.0, 1.0);
    } else {
      _lastScrollDepth = 1.0;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Cache the service here (safe during build phase) so dispose() can use
    // it without touching ref after the widget is unmounted.
    _interactionService = ref.read(interactionServiceProvider);
  }

  @override
  void deactivate() {
    // Capture final scroll position BEFORE child disposal detaches the
    // controller. `deactivate` fires on unmount before any child's
    // `dispose`, so `hasClients` is still true here.
    if (_scrollController.hasClients) {
      final ScrollPosition pos = _scrollController.position;
      if (pos.maxScrollExtent > 0) {
        _lastScrollDepth =
            (pos.pixels / pos.maxScrollExtent).clamp(0.0, 1.0);
      } else {
        _lastScrollDepth ??= 1.0;
      }
    }
    super.deactivate();
  }

  @override
  void dispose() {
    final double readSec =
        DateTime.now().difference(_openedAt).inMilliseconds / 1000.0;
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    _interactionService.trackEvent(
      InteractionEvent(
        contentId: widget.articleId,
        action: InteractionAction.close,
        durationSec: readSec,
        timestamp: _openedAt,
        scrollDepth: _lastScrollDepth,
      ),
    );
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<ContentItem> itemAsync =
        ref.watch(contentItemProvider(widget.articleId));

    return Scaffold(
      backgroundColor: NFColors.bg,
      body: SafeArea(
        child: itemAsync.when(
          loading: () => const _DetailSkeleton(),
          error: (_, _) => const _DetailError(),
          data: (ContentItem item) => _DetailBody(
            item: item,
            scrollController: _scrollController,
          ),
        ),
      ),
    );
  }
}

class _DetailBody extends ConsumerWidget {
  const _DetailBody({required this.item, required this.scrollController});

  final ContentItem item;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final Breakpoint bp = context.breakpoint;
    final double maxWidth = bp == Breakpoint.mobile ? double.infinity : 720;

    return SingleChildScrollView(
      controller: scrollController,
      physics: const AlwaysScrollableScrollPhysics(),
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 8, 14, 120),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                _BackButton(onTap: () => context.pop()),
                const SizedBox(height: 12),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: SourceLine(
                    source: item.sourceName?.isNotEmpty == true
                        ? item.sourceName!
                        : item.authorName ?? 'Источник',
                    time: item.publishedAt ?? DateTime.now(),
                    readTime: item.metadata?.readTimeMinutes != null
                        ? '${item.metadata!.readTimeMinutes} мин чтения'
                        : null,
                  ),
                ),
                // Skip the huge hero title block entirely when the post
                // has no title (most Telegram items). Otherwise we'd
                // render a single stray emoji with display-size letter-
                // spacing, creating a big empty band above the image.
                if (item.displayTitle.trim().isNotEmpty) ...<Widget>[
                  const SizedBox(height: 12),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: _DetailTitle(title: item.displayTitle, bp: bp),
                  ),
                ],
                if (item.hasMedia) ...<Widget>[
                  SizedBox(height: item.displayTitle.trim().isNotEmpty ? 18 : 12),
                  _HeroImage(item: item),
                ],
                const SizedBox(height: 16),
                _ActionBar(articleId: item.id, url: item.url),
                const SizedBox(height: 22),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: _DetailBodyText(item: item),
                ),
                if (item.relatedIds.isNotEmpty)
                  RelatedRail(
                    parentId: item.id,
                    relatedIds: item.relatedIds,
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BackButton extends StatelessWidget {
  const _BackButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Назад в ленту',
      button: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.fromLTRB(8, 8, 14, 10),
          decoration: BoxDecoration(
            color: NFColors.surface,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: NFColors.hairline, width: 1),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: const <Widget>[
              NFIcon('back', size: 16, color: NFColors.ink),
              SizedBox(width: 6),
              Text(
                'Назад в ленту',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  letterSpacing: -0.1,
                  color: NFColors.ink,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DetailTitle extends StatelessWidget {
  const _DetailTitle({required this.title, required this.bp});

  final String title;
  final Breakpoint bp;

  @override
  Widget build(BuildContext context) {
    final double fontSize = bp == Breakpoint.mobile ? 28 : 46;
    final double letterSpacing = bp == Breakpoint.mobile ? -1 : -1.6;
    final double lineHeight = bp == Breakpoint.mobile ? 1.1 : 1.05;
    return Text(
      title,
      style: TextStyle(
        fontFamily: 'Nunito',
        fontSize: fontSize,
        fontWeight: FontWeight.w800,
        letterSpacing: letterSpacing,
        height: lineHeight,
        color: NFColors.ink,
      ),
    );
  }
}

class _HeroImage extends StatelessWidget {
  const _HeroImage({required this.item});

  final ContentItem item;

  @override
  Widget build(BuildContext context) {
    const double height = 380;
    final BorderRadius radius = NFRadii.brLg;

    // Partition media into resolved video / image lists.
    final List<String> imageUrls = item.media
        .where((m) => m.type.toLowerCase() == 'image' || m.type.isEmpty)
        .map((m) => MediaConfig.resolve(m.url))
        .whereType<String>()
        .toList(growable: false);
    final String? videoUrl = item.media
        .where((m) => m.type.toLowerCase() == 'video')
        .map((m) => MediaConfig.resolve(m.url))
        .whereType<String>()
        .firstOrNull;

    final StripeTone tone = _toneFor(item);
    final int seed = item.id.hashCode & 0x7fffffff;

    Widget placeholder({String label = 'ФОТО'}) => StripePlaceholder(
          height: height,
          tone: tone,
          seed: seed,
          label: label,
          radius: 0,
        );

    Widget inner;
    if (videoUrl != null) {
      inner = ClipRRect(
        borderRadius: radius,
        child: SizedBox(
          height: height,
          width: double.infinity,
          child: _InlineVideoPlayer(url: videoUrl, fallback: placeholder(label: 'ВИДЕО')),
        ),
      );
    } else if (imageUrls.isEmpty) {
      inner = ClipRRect(borderRadius: radius, child: placeholder());
    } else {
      inner = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => MediaViewer.show(context, imageUrls: imageUrls),
        child: ClipRRect(
          borderRadius: radius,
          child: SizedBox(
            height: height,
            width: double.infinity,
            child: CachedNetworkImage(
              imageUrl: imageUrls.first,
              fit: BoxFit.cover,
              placeholder: (_, _) => placeholder(),
              errorWidget: (_, _, _) => placeholder(),
            ),
          ),
        ),
      );
    }

    // When there are extra images beyond the hero, render a small
    // thumbnail strip below so users can tap into the full-screen
    // gallery starting at the right index.
    if (videoUrl == null && imageUrls.length > 1) {
      return FeedHero(
        id: item.id,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            inner,
            const SizedBox(height: 8),
            _ThumbnailStrip(urls: imageUrls),
          ],
        ),
      );
    }
    return FeedHero(id: item.id, child: inner);
  }

  static StripeTone _toneFor(ContentItem item) {
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
}

/// Small horizontal thumbnail strip shown under the hero when the post
/// has more than one image. Tap opens the full gallery at the tapped
/// index.
class _ThumbnailStrip extends StatelessWidget {
  const _ThumbnailStrip({required this.urls});

  final List<String> urls;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 72,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: urls.length,
        separatorBuilder: (_, _) => const SizedBox(width: 6),
        itemBuilder: (context, index) {
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () => MediaViewer.show(
              context,
              imageUrls: urls,
              initialIndex: index,
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: SizedBox(
                width: 96,
                height: 72,
                child: CachedNetworkImage(
                  imageUrl: urls[index],
                  fit: BoxFit.cover,
                  placeholder: (_, _) => const ColoredBox(color: NFColors.surface2),
                  errorWidget: (_, _, _) => const ColoredBox(color: NFColors.surface2),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Simple controls-enabled video player. On web uses the standard
/// `<video controls>` element via `video_player_web`; mobile gets the
/// native player. Tap the frame to toggle play / pause.
class _InlineVideoPlayer extends StatefulWidget {
  const _InlineVideoPlayer({required this.url, required this.fallback});

  final String url;
  final Widget fallback;

  @override
  State<_InlineVideoPlayer> createState() => _InlineVideoPlayerState();
}

class _InlineVideoPlayerState extends State<_InlineVideoPlayer> {
  VideoPlayerController? _controller;
  bool _initialised = false;
  bool _errored = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    try {
      final c = VideoPlayerController.networkUrl(Uri.parse(widget.url));
      _controller = c;
      await c.initialize();
      if (!mounted) return;
      setState(() => _initialised = true);
    } catch (_) {
      if (!mounted) return;
      setState(() => _errored = true);
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_errored) return widget.fallback;
    if (!_initialised || _controller == null) {
      return Stack(
        alignment: Alignment.center,
        children: <Widget>[widget.fallback, const VideoPlayOverlay()],
      );
    }
    final c = _controller!;
    return Stack(
      alignment: Alignment.center,
      children: <Widget>[
        AspectRatio(aspectRatio: c.value.aspectRatio, child: VideoPlayer(c)),
        _VideoControls(controller: c),
      ],
    );
  }
}

class _VideoControls extends StatefulWidget {
  const _VideoControls({required this.controller});

  final VideoPlayerController controller;

  @override
  State<_VideoControls> createState() => _VideoControlsState();
}

class _VideoControlsState extends State<_VideoControls> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onTick);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onTick);
    super.dispose();
  }

  void _onTick() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final playing = widget.controller.value.isPlaying;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => playing
          ? widget.controller.pause()
          : widget.controller.play(),
      child: AnimatedOpacity(
        opacity: playing ? 0 : 1,
        duration: const Duration(milliseconds: 180),
        child: const Center(child: VideoPlayOverlay()),
      ),
    );
  }
}

class _ActionBar extends ConsumerWidget {
  const _ActionBar({required this.articleId, required this.url});

  final String articleId;
  final String? url;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ArticleActionState actionState = ref.watch(
      articleActionsNotifierProvider.select(
        (Map<String, ArticleActionState> map) =>
            map[articleId] ?? const ArticleActionState(),
      ),
    );
    final ArticleActionsNotifier notifier =
        ref.read(articleActionsNotifierProvider.notifier);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: NFColors.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: NFColors.hairline, width: 1),
      ),
      child: Row(
        children: <Widget>[
          ReactionBar(
            isLiked: actionState.isLiked,
            isDisliked: actionState.isDisliked,
            isBookmarked: actionState.isSaved,
            onLike: () => notifier.like(articleId),
            onDislike: () => notifier.dislike(articleId),
            onBookmark: () => notifier.toggleSave(articleId),
            compact: true,
          ),
          const Spacer(),
          if (url != null && url!.isNotEmpty)
            _OpenOriginalPill(url: url!),
        ],
      ),
    );
  }
}

class _OpenOriginalPill extends StatelessWidget {
  const _OpenOriginalPill({required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Открыть источник',
      button: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => DetailScreenController.openOriginal(url),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: const Color(0x00000000),
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: NFColors.hairline, width: 1),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: const <Widget>[
              NFIcon('external', size: 13, color: NFColors.ink),
              SizedBox(width: 6),
              Text(
                'Открыть источник',
                style: TextStyle(
                  fontFamily: 'Nunito',
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600,
                  color: NFColors.ink,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Article body — preserves the existing HTML / plain-text / description
/// fallback chain from the legacy screen so `flutter_widget_from_html_core`
/// continues to render Habr / VC.RU payloads, and Telegram plain-text
/// messages keep their paragraph splitting (`\n\n`).
class _DetailBodyText extends StatelessWidget {
  const _DetailBodyText({required this.item});

  final ContentItem item;

  static const TextStyle _paragraphStyle = TextStyle(
    fontFamily: 'Nunito',
    fontSize: 17,
    height: 1.7,
    color: NFColors.ink2,
  );

  @override
  Widget build(BuildContext context) {
    final String? html = item.bestContentHtml;
    if (item.isHtml && html != null && html.isNotEmpty) {
      return HtmlWidget(
        html,
        textStyle: _paragraphStyle,
        customWidgetBuilder: (element) {
          if (element.localName != 'img') return null;
          final String? rawSrc = element.attributes['src'];
          if (rawSrc == null || rawSrc.isEmpty) return null;
          final String resolved = MediaConfig.resolve(rawSrc) ?? rawSrc;
          if (resolved.isEmpty) return null;

          double? widthAttr =
              double.tryParse(element.attributes['width'] ?? '');
          double? heightAttr =
              double.tryParse(element.attributes['height'] ?? '');
          final String alt =
              element.attributes['alt'] ?? element.attributes['title'] ?? '';

          final double? intrinsicAspect = (widthAttr != null &&
                  heightAttr != null &&
                  widthAttr > 0 &&
                  heightAttr > 0)
              ? widthAttr / heightAttr
              : null;

          final Widget image = Image.network(
            resolved,
            fit: BoxFit.contain,
            alignment: Alignment.center,
            semanticLabel: alt.isNotEmpty ? alt : null,
            loadingBuilder: (context, child, progress) {
              if (progress == null) return child;
              return const ColoredBox(color: NFColors.surface2);
            },
            errorBuilder: (_, _, _) => GestureDetector(
              onTap: () async {
                final Uri? uri = Uri.tryParse(resolved);
                if (uri != null) {
                  await launchUrl(
                    uri,
                    mode: LaunchMode.externalApplication,
                  );
                }
              },
              child: Container(
                padding: const EdgeInsets.all(12),
                color: NFColors.surface2,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: const <Widget>[
                    Icon(Icons.image_not_supported_outlined,
                        size: 16, color: NFColors.mute),
                    SizedBox(width: 8),
                    Flexible(
                      child: Text(
                        'Открыть изображение в браузере',
                        style: TextStyle(
                          fontFamily: 'Nunito',
                          fontSize: 13,
                          color: NFColors.mute,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );

          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: intrinsicAspect != null
                  ? AspectRatio(aspectRatio: intrinsicAspect, child: image)
                  : image,
            ),
          );
        },
        onTapUrl: (String url) async {
          final Uri? uri = Uri.tryParse(url);
          if (uri != null) {
            await launchUrl(uri, mode: LaunchMode.externalApplication);
          }
          return true;
        },
      );
    }

    final String? text = item.bestContentText ?? item.description;
    if (text == null || text.isEmpty) return const SizedBox.shrink();

    final List<String> paragraphs = text
        .split(RegExp(r'\n{2,}'))
        .map((String p) => p.trim())
        .where((String p) => p.isNotEmpty)
        .toList();

    if (paragraphs.isEmpty) {
      return Text(text, style: _paragraphStyle);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        for (int i = 0; i < paragraphs.length; i++) ...<Widget>[
          if (i > 0) const SizedBox(height: 14),
          Text(paragraphs[i], style: _paragraphStyle),
        ],
      ],
    );
  }
}

class _DetailSkeleton extends StatelessWidget {
  const _DetailSkeleton();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _SkeletonBar(width: 140, height: 36, radius: 999),
          SizedBox(height: 24),
          _SkeletonBar(width: 180, height: 14, radius: 4),
          SizedBox(height: 12),
          _SkeletonBar(width: double.infinity, height: 32, radius: 6),
          SizedBox(height: 8),
          _SkeletonBar(width: 280, height: 32, radius: 6),
          SizedBox(height: 18),
          _SkeletonBar(width: double.infinity, height: 380, radius: 26),
        ],
      ),
    );
  }
}

class _SkeletonBar extends StatelessWidget {
  const _SkeletonBar({
    required this.width,
    required this.height,
    required this.radius,
  });

  final double width;
  final double height;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: NFColors.surface2,
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}

class _DetailError extends StatelessWidget {
  const _DetailError();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const NFIcon('radar', size: 48, color: NFColors.mute2),
            const SizedBox(height: 12),
            const Text(
              'Не удалось загрузить статью.',
              style: TextStyle(
                fontFamily: 'Nunito',
                fontSize: 15,
                color: NFColors.ink,
              ),
            ),
            TextButton(
              onPressed: () => context.pop(),
              child: const Text(
                'Назад',
                style: TextStyle(color: NFColors.accent),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
