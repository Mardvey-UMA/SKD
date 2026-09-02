/// Breakpoint enumeration and thresholds.
///
/// Thresholds (lowered relative to the original `useLayout()` spec so the
/// SideNav desktop layout kicks in on typical laptop windows, not just on
/// ultra-wide monitors):
///
/// - `mobile`  — width `< 720px`  (narrow phones + narrow browser windows)
/// - `tablet`  — `720px .. 1023px` (large phones landscape, small tablets)
/// - `desktop` — `>= 1024px`       (iPad-landscape and up — side nav visible)
enum Breakpoint { mobile, tablet, desktop }

/// Thresholds (inclusive upper bounds) used to classify a width into a
/// [Breakpoint]. Values are logical pixels, matching the fluid web layout.
class Breakpoints {
  const Breakpoints._();

  /// Maximum width (inclusive) still considered mobile.
  static const double mobileMax = 719;

  /// Maximum width (inclusive) still considered tablet.
  static const double tabletMax = 1023;

  /// Returns the [Breakpoint] for a given logical [width].
  static Breakpoint fromWidth(double width) {
    if (width <= mobileMax) return Breakpoint.mobile;
    if (width <= tabletMax) return Breakpoint.tablet;
    return Breakpoint.desktop;
  }
}
