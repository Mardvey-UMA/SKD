import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../models/ad.dart';
import '../../domain/models/content_item.dart';

/// Entry in the rendered feed sequence — either a content item or an
/// injected sponsored [Ad].
sealed class FeedEntry {
  const FeedEntry();
}

class FeedItemEntry extends FeedEntry {
  const FeedItemEntry(this.item, this.positionInFeed);
  final ContentItem item;

  /// 1-indexed position among real content items (ads are excluded).
  /// Used for `position_in_feed` in interaction events.
  final int positionInFeed;
}

class FeedAdEntry extends FeedEntry {
  const FeedAdEntry(this.ad, this.key);
  final Ad ad;
  final String key;
}

/// Ad pool — mirrors `ADS` in `design/reference/mockup/data.jsx` 1:1.
const List<Ad> kFeedAds = <Ad>[
  Ad(
    brand: 'Skillbox',
    tagline: 'Скидка 45% на курсы до конца недели',
    title: 'Стать data-аналитиком за 6 месяцев',
    desc:
        'Онлайн-обучение с проектами в портфолио и наставником. Первые две недели бесплатно.',
    cta: 'Узнать подробнее',
  ),
  Ad(
    brand: 'Т-Банк',
    tagline: 'Бизнес-карта · обслуживание 0 ₽',
    title: 'Откройте счёт для бизнеса онлайн',
    desc:
        'Без визита в банк, за 10 минут. Кэшбэк до 7% на B2B-сервисы.',
    cta: 'Открыть счёт',
  ),
  Ad(
    brand: 'Notion AI',
    tagline: 'Попробуй бесплатно',
    title: 'Все заметки, задачи и доки в одном рабочем пространстве',
    desc:
        'Автоматическое саммари встреч, поиск по базе знаний и шаблоны под любую команду.',
    cta: 'Начать',
  ),
];

/// Immutable view of UI-only state for the new Feed screen.
///
/// Holds presentation concerns that do NOT touch the data layer —
/// hidden-ad set, ad frequency. Core feed data (items / pagination /
/// feedRequestId) continues to live in `FeedNotifier`.
class FeedScreenState {
  const FeedScreenState({
    this.hiddenAds = const <String>{},
    this.adFrequency = 5,
  });

  final Set<String> hiddenAds;
  final int adFrequency;

  FeedScreenState copyWith({
    Set<String>? hiddenAds,
    int? adFrequency,
  }) => FeedScreenState(
        hiddenAds: hiddenAds ?? this.hiddenAds,
        adFrequency: adFrequency ?? this.adFrequency,
      );
}

/// Presentation-layer controller for the redesigned Feed screen.
///
/// Owns hidden-ad set + ad frequency only. All data / side-effects
/// (pagination, retry, refresh, interaction batching) remain in the existing
/// `FeedNotifier` and `InteractionService` — this controller does NOT mirror
/// or proxy them.
class FeedScreenController extends Notifier<FeedScreenState> {
  @override
  FeedScreenState build() => const FeedScreenState();

  /// Adds [adId] to the hidden set so subsequent `buildEntries` invocations
  /// skip it. Already-hidden IDs are ignored.
  void hideAd(String adId) {
    if (state.hiddenAds.contains(adId)) return;
    state = state.copyWith(
      hiddenAds: <String>{...state.hiddenAds, adId},
    );
  }
}

final feedScreenControllerProvider =
    NotifierProvider<FeedScreenController, FeedScreenState>(
  FeedScreenController.new,
);

/// Builds the rendered feed sequence: each real item, then every
/// `adFrequency`-th slot injects an ad from [kFeedAds] (filtered by
/// [hiddenAds]). Never injects at position 0 or after the last item, and
/// never two ads adjacent.
///
/// Mirrors the `useMemo` sequence in
/// `design/reference/mockup/screens.jsx` → `FeedScreen` 1:1. Pure — safe to
/// call during `build()`; callers should memoise on (items, hiddenAds,
/// adFrequency).
List<FeedEntry> buildFeedEntries({
  required List<ContentItem> items,
  required Set<String> hiddenAds,
  required int adFrequency,
}) {
  if (items.isEmpty) return const <FeedEntry>[];
  final List<Ad> available =
      kFeedAds.where((Ad a) => !hiddenAds.contains(_adId(a))).toList();
  final List<FeedEntry> seq = <FeedEntry>[];
  final bool injectAds = adFrequency >= 2 && available.isNotEmpty;
  int adIdx = 0;
  for (int i = 0; i < items.length; i++) {
    seq.add(FeedItemEntry(items[i], i + 1));
    final bool isLast = i == items.length - 1;
    if (injectAds && (i + 1) % adFrequency == 0 && i > 0 && !isLast) {
      final Ad ad = available[adIdx % available.length];
      seq.add(FeedAdEntry(ad, 'ad-$i-$adIdx'));
      adIdx++;
    }
  }
  return seq;
}

String _adId(Ad ad) => ad.brand;
