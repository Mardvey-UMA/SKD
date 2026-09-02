/// Sponsored content payload driving the three [AdStyle] variants.
///
/// Mirrors the JSX `ad` prop consumed by `AdCard` in
/// `design/reference/mockup/cards.jsx`. The same model serves `subtle`,
/// `card`, and `banner` — individual variants select the subset they need.
class Ad {
  const Ad({
    required this.brand,
    required this.tagline,
    required this.title,
    required this.desc,
    required this.cta,
  });

  /// Brand / advertiser name (e.g. `Yandex Pay`). Rendered as the mono
  /// «РЕКЛАМА · BRAND» label (always uppercased at the widget layer) and
  /// as the giant wordmark on the `card` style / first-letter tile on the
  /// `banner` style.
  final String brand;

  /// Short one-liner surfacing next to the title on the `subtle` and
  /// `banner` styles.
  final String tagline;

  /// Headline copy — 18/w700 on `subtle` + `card`, 15/w700 on `banner`.
  final String title;

  /// Body description — only rendered on the `card` style.
  final String desc;

  /// Call-to-action label inside the ink pill — only rendered on `subtle`
  /// and `card` styles.
  final String cta;
}
