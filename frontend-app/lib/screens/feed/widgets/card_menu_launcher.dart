import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../../ui/sheets/card_menu.dart';

/// Opens a [CardMenu] via [Overlay.of] so the sheet lives above the
/// `ResponsiveShell` chrome.
///
/// Returns once the sheet has been dismissed. The menu closes on scrim tap,
/// «Отмена» row, or after [onAddToSpace] / [onHideSource] fire — callers
/// need not manage the overlay lifecycle.
///
/// Call-sites pass already-bound callbacks; this helper only handles
/// present / dismiss mechanics so the presentation layer stays declarative.
Future<void> openCardMenu(
  BuildContext context, {
  required String sourceTitle,
  required String sourceHandle,
  required bool isPremium,
  required VoidCallback onAddToSpace,
  required VoidCallback onHideSource,
}) async {
  final OverlayState overlay = Overlay.of(context, rootOverlay: true);
  final Completer<void> closed = Completer<void>();
  late OverlayEntry entry;

  void dismiss() {
    if (closed.isCompleted) return;
    entry.remove();
    closed.complete();
  }

  entry = OverlayEntry(
    builder: (_) => CardMenu(
      sourceTitle: sourceTitle,
      sourceHandle: sourceHandle,
      isPremium: isPremium,
      onClose: dismiss,
      onAddToSpace: () {
        dismiss();
        onAddToSpace();
      },
      onHideSource: () {
        dismiss();
        onHideSource();
      },
    ),
  );

  overlay.insert(entry);
  return closed.future;
}
