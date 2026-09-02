import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:smooth_page_indicator/smooth_page_indicator.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/config/media_config.dart';
import '../../domain/models/content_item.dart';
import '../../../../theme/tokens/app_colors.dart';
import '../../../../theme/tokens/app_radii.dart';

class MediaGalleryWidget extends StatefulWidget {
  const MediaGalleryWidget({
    super.key,
    required this.media,
  });

  final List<MediaItem> media;

  @override
  State<MediaGalleryWidget> createState() => _MediaGalleryWidgetState();
}

class _MediaGalleryWidgetState extends State<MediaGalleryWidget> {
  late final PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final items = widget.media
        .where((m) => m.type == 'image' || m.type == 'video')
        .toList();
    if (items.isEmpty) return const SizedBox.shrink();

    if (items.length == 1) {
      return _buildSingleItem(items.first);
    }

    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: AppRadii.lg),
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          SizedBox(
            height: 180,
            child: PageView.builder(
              controller: _pageController,
              itemCount: items.length,
              itemBuilder: (context, index) => _buildMediaTile(items[index]),
            ),
          ),
          _CoverOverlay(),
          Positioned(
            bottom: 8,
            child: SmoothPageIndicator(
              controller: _pageController,
              count: items.length,
              effect: const WormEffect(
                dotHeight: 6,
                dotWidth: 6,
                activeDotColor: Colors.white,
                dotColor: Colors.white54,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSingleItem(MediaItem item) {
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: AppRadii.lg),
      child: Stack(
        children: [
          SizedBox(
            height: 180,
            width: double.infinity,
            child: _buildMediaTile(item),
          ),
          _CoverOverlay(),
        ],
      ),
    );
  }

  Widget _buildMediaTile(MediaItem item) {
    if (item.type == 'video') {
      return _buildVideoPlaceholder(item);
    }
    return _buildImageTile(item);
  }

  Widget _buildVideoPlaceholder(MediaItem item) {
    return GestureDetector(
      onTap: () async {
        final uri = Uri.tryParse(item.url);
        if (uri != null) {
          await launchUrl(uri, mode: LaunchMode.externalApplication);
        }
      },
      child: Stack(
        alignment: Alignment.center,
        children: [
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  AppColors.surfaceSunken,
                  AppColors.surfaceBackgroundSubtle,
                ],
              ),
            ),
          ),
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: Colors.black.withAlpha(120),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.play_arrow_rounded,
              size: 40,
              color: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildImageTile(MediaItem item) {
    return CachedNetworkImage(
      imageUrl: MediaConfig.resolve(item.url) ?? item.url,
      fit: BoxFit.cover,
      placeholder: (context, url) => Container(
        color: AppColors.surfaceBackgroundSubtle,
        child: const Center(
          child: SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      errorWidget: (context, url, error) => Container(
        color: AppColors.surfaceBackgroundSubtle,
        child: const Center(
          child: Icon(
            Icons.broken_image_outlined,
            size: 32,
            color: AppColors.textTertiary,
          ),
        ),
      ),
    );
  }
}

class _CoverOverlay extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Positioned(
      bottom: 0,
      left: 0,
      right: 0,
      child: Container(
        height: 60,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.transparent, Color(0x14000000)],
          ),
        ),
      ),
    );
  }
}
