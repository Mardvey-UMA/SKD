import 'package:flutter/widgets.dart';

import '../../theme/radii.dart';
import 'page_transitions.dart';

/// Wraps an image area in a [Hero] keyed by `feed-hero-{id}` so the feed
/// card's stripe / photo morphs smoothly into the detail screen's hero
/// image. Used on both sides of the Feed → Detail transition.
///
/// The [FlightShuttleBuilder] clips the flying widget to the large card
/// corner radius so the stripe pattern stays visually aligned throughout
/// the flight (prevents corner snap at start / end of the animation).
class FeedHero extends StatelessWidget {
  const FeedHero({
    super.key,
    required this.id,
    required this.child,
  });

  /// Content item id — must match the id used on both the feed card and
  /// the detail screen for the hero flight to resolve.
  final String id;

  final Widget child;

  static String tagFor(String id) => 'feed-hero-$id';

  @override
  Widget build(BuildContext context) {
    return Hero(
      tag: tagFor(id),
      createRectTween: (Rect? begin, Rect? end) =>
          RectTween(begin: begin, end: end),
      flightShuttleBuilder: _shuttle,
      child: child,
    );
  }

  static Widget _shuttle(
    BuildContext flightContext,
    Animation<double> animation,
    HeroFlightDirection flightDirection,
    BuildContext fromHeroContext,
    BuildContext toHeroContext,
  ) {
    final Hero target = toHeroContext.widget as Hero;
    return ClipRRect(
      borderRadius: BorderRadius.circular(NFRadii.radiusLg),
      child: target.child,
    );
  }
}

/// Duration + curve used by Flutter's [Hero] machinery for feed-to-detail
/// transitions. Exposed as constants so tests and the detail screen can
/// assert the contract.
class FeedHeroMotion {
  const FeedHeroMotion._();

  static Duration get duration => NFMotion.heroDuration;
  static Curve get curve => NFMotion.heroCurve;
}
