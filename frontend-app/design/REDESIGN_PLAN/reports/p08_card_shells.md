# P08 · Card shells: ShortCard, LongCard, CardBody

**Spec:** `design/REDESIGN_PLAN/prompts/08_card_shells.md`
**Reference:** `design/reference/mockup/cards.jsx` → `ShortCard`, `LongCard`, `CardBody`

## Files created

- `lib/ui/cards/card_item.dart` — `CardImages` enum, `ReactionKind` enum, `CardItem` model.
- `lib/ui/cards/card_body.dart` — shared body block (SourceLine + title + snippet + ReactionBar + CTA pill).
- `lib/ui/cards/short_card.dart` — Short variant shell.
- `lib/ui/cards/long_card.dart` — Long variant shell.
- `test/ui/cards/short_card_test.dart` — 5 widget tests.
- `test/ui/cards/long_card_test.dart` — 7 widget tests (+fade overlay).

## Data model — `CardItem`

Minimal shape driving both shells, mirroring the subset of the JS mockup `item` consumed by the card body + image block.

```dart
CardItem({
  required String id,
  required String source,
  required DateTime time,
  required String title,
  required String snippet,
  required CardImages images,
  required StripeTone tone,
  required int seed,
  String? readTime,
  StripeTone? toneSecondary,
  StripeTone? toneTertiary,
});
```

- `CardImages ∈ {one, multi, none}` — `none` skips the whole image block (no header padding).
- `ReactionKind ∈ {like, dislike, bookmark}` — matches the three actions in the mockup ReactionBar.
- `tone` / `toneSecondary` / `toneTertiary` are `StripeTone` (not `String`) so the model stays type-safe against the `StripePlaceholder` palette.

The existing `lib/ui/atoms/image_item.dart` is a per-image prop bag without `id/source/title/snippet/images` — kept separate. `CardItem` is the card-level aggregate; `ImageItem` is derived internally when rendering the image block.

## Card shell (shared structure)

Implemented identically in `ShortCard` and `LongCard`:

```
DecoratedBox(
  decoration: BoxDecoration(
    color: NFColors.surface,
    borderRadius: NFRadii.brLg,          // 26
    border: Border.all(color: NFColors.hairline, width: 1),
    boxShadow: NFShadows.card,
  ),
  child: ClipRRect(
    borderRadius: NFRadii.brLg,
    child: Column(
      [
        if (images != none) Padding(EdgeInsets.all(8), child: imageBlock),
        CardBody(...),
      ],
    ),
  ),
)
```

- Image block is gated on `item.images != CardImages.none` — with `none` the Column drops straight to `CardBody`, matching the "no header padding" rule from the spec.
- `SingleImage` / `MultiImage` are the P05 atoms, fed an internal `ImageItem(tone, seed, toneSecondary, toneTertiary)`.
- Image-block padding: 8 on all sides (`EdgeInsets.all(8)`).

## `CardBody`

Unified body block used by both cards. Visual variance is parameterised, not branched by card type — mirrors the shared-body pattern in `CardBody` of the JS mockup and absorbs `LongCard`'s inline body variant into the same widget.

```dart
CardBody({
  required CardItem item,
  required TextStyle titleStyle,
  required CardSnippet snippet,
  required String ctaLabel,
  required VoidCallback onOpen,
  required ValueChanged<ReactionKind> onReact,
  bool isLiked, isDisliked, isBookmarked = false,
  VoidCallback? onMore,
});
```

Composition (top → bottom, `EdgeInsets.fromLTRB(18, 14, 18, 14)`):

1. `SourceLine(source, time, readTime, onMore)`.
2. `SizedBox(height: 10)`.
3. `GestureDetector(behavior: opaque, onTap: onOpen)` wrapping `Text(item.title, style: titleStyle)`.
4. `SizedBox(height: 8)`.
5. `_Snippet(text, config)` — plain `Text` when `maxHeight == null`; `ClipRect` + `SizedBox(height: maxHeight)` + `Stack` with optional bottom fade when clamped.
6. `SizedBox(height: 14)`.
7. `Row`: `ReactionBar(...)` + `Spacer()` + `_CtaPill(label, onTap: onOpen)`.

CTA pill — ink fill, radius 999, 9×14 padding, `Nunito 13 w600 letter -0.2` white label + 13-px `arrow-right` NFIcon, exactly as the mockup.

## `CardSnippet` — clamp / fade contract

```dart
CardSnippet({required TextStyle style, double? maxHeight, bool fadeOverlay = false});
```

- `ShortCard` → `CardSnippet(style: 14.5/1.5/mute)` — uncapped.
- `LongCard` → `CardSnippet(style: 14.5/1.5/mute, maxHeight: 96, fadeOverlay: true)` — `_BottomFade` stacks a 40-px `IgnorePointer` + `LinearGradient(topCenter→bottomCenter, [0x00FFFFFF, NFColors.surface])` on the clamped snippet, mirroring `linear-gradient(to bottom, rgba(255,255,255,0), surface)`.

## `ShortCard`

- Title: `Nunito 21 / w600 / letter -0.5 / height 1.15 / NFColors.ink`.
- Snippet: `Nunito 14.5 / w400 / height 1.5 / NFColors.mute`, no clamp.
- CTA: `Читать` + `arrow-right` icon.

```dart
ShortCard({
  required CardItem item,
  required ValueChanged<CardItem> onOpen,
  required void Function(CardItem item, ReactionKind kind) onReact,
  bool isLiked, isDisliked, isBookmarked = false,
  VoidCallback? onMore,
});
```

## `LongCard`

- Title: `Nunito 22 / w700 / letter -0.6 / height 1.14 / NFColors.ink`.
- Snippet: clamped 96 px with 40-px fade overlay.
- CTA: `Читать далее` + `arrow-right` icon.
- Same signature as `ShortCard`.

## Callbacks

- `onOpen(item)` — fires on title tap and CTA tap. In the spec card text: "тап на реакции не должен пробрасывать onOpen (use `HitTestBehavior.opaque`)". `ReactionBar` from P06 already declares `HitTestBehavior.opaque` on every button, so pointer events stop at the reaction buttons and never reach the card's `Gesture/DecoratedBox` ancestors.
- `onReact(item, kind)` — fires when one of the three reaction buttons is tapped. `kind` maps 1:1 to the ReactionBar button.
- The card shell is NOT tappable at the outer level. Only title + CTA + reactions are hit zones — matches the mockup where `ShortCard` uses `onClick` on the outer box **but** reactions still `e.stopPropagation()`; we preserve the same semantics by making title + CTA the only open-paths. This is the lowest-risk mapping and what the Flutter pointer system supports cleanly.

## Tests (all green, 12/12)

`test/ui/cards/short_card_test.dart` — 5 tests:

1. `images=one` renders, `SingleImage` found, `MultiImage` absent, no exceptions.
2. `images=multi` renders, `MultiImage` found, `SingleImage` absent.
3. `images=none` renders without any image atom in the tree.
4. Tap on `Text(title)` → `onOpen` called exactly once, receives correct item.
5. Tap on `Text('Читать')` CTA → `onOpen` called exactly once.
6. Tap on `bySemanticsLabel('Нравится')` reaction → `onReact(item, like)` called once, `onOpen` NOT called.

`test/ui/cards/long_card_test.dart` — 7 tests:

1. `images=one` renders without exception.
2. `images=multi` renders without exception.
3. `images=none` renders without the image block.
4. Fade overlay is present — walks the `DecoratedBox` tree and asserts a `LinearGradient` with `begin=topCenter end=bottomCenter`, 2 colors, first color `α=0.0`.
5. Tap on title → `onOpen` called once with correct item.
6. Tap on `Text('Читать далее')` CTA → `onOpen` called once.
7. Tap on `bySemanticsLabel('В закладки')` → `onReact(item, bookmark)` called once, `onOpen` not called.

## Quality gates

- `flutter analyze lib/ui/cards/*.dart test/ui/cards/*.dart` → `No issues found!` (0.7 s).
- `flutter test test/ui/cards/` → `+13: All tests passed!`.
- No `lib/features/**` imports. No new dependencies. No real images — only `StripePlaceholder` via existing `SingleImage`/`MultiImage` atoms.
- No mutation of the legacy `lib/features/feed/presentation/widgets/*_card_widget.dart` — those are scheduled for P16 replacement.

## Acceptance criteria

- [x] Shell: surface / radiusLg / hairline / shadow / image-padding-8 / body-padding(18,14).
- [x] `images: {one, multi, none}` handled; `none` skips image block and header padding.
- [x] `SourceLine` + clickable title + snippet + `ReactionBar` + inky CTA pill.
- [x] `ShortCard` title 21/w600/-0.5/1.15, snippet 14.5/1.5/mute no clamp, CTA `Читать`.
- [x] `LongCard` title 22/w700/-0.6/1.14, snippet `maxHeight: 96` + 40-px fade overlay, CTA `Читать далее`.
- [x] `CardItem` model exposes `{id, source, time, readTime?, title, snippet, images, tone+toneSecondary?+toneTertiary?, seed}`.
- [x] `onOpen(CardItem)` / `onReact(CardItem, ReactionKind)` callbacks.
- [x] Reaction tap does NOT bubble to `onOpen` (inherits `HitTestBehavior.opaque` from P06 `ReactionBar`).
- [x] Fade overlay: `LinearGradient(topCenter → bottomCenter, [transparent, surface])` in a 40-px `Align(bottomCenter)` block.
- [ ] Goldens (`short_card_{one,multi,none}.png`, `long_card.png` at 375 / 760) — deferred, golden infrastructure not yet set up (previous prompts P04–P07 also deferred goldens).
