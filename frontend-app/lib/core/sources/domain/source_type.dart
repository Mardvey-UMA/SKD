enum SourceType {
  telegram('TELEGRAM', 'TG'),
  habr('HABR', 'Habr'),
  vcRu('VCRU', 'VC');

  const SourceType(this.wire, this.shortLabel);

  final String wire;
  final String shortLabel;

  static SourceType? fromWire(String? raw) {
    if (raw == null) return null;
    final upper = raw.toUpperCase();
    // Exact match first.
    for (final t in values) {
      if (t.wire == upper) return t;
    }
    // Prefix match for subtypes: HABR_HUB, HABR_USER, VCRU_COMPANY, etc.
    if (upper.startsWith('HABR')) return SourceType.habr;
    if (upper.startsWith('VCRU') || upper.startsWith('VC_RU') || upper == 'VC.RU') {
      return SourceType.vcRu;
    }
    if (upper.startsWith('TELEGRAM') || upper == 'TG') {
      return SourceType.telegram;
    }
    return null;
  }
}
