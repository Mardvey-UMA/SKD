import 'package:flutter/material.dart';

import '../../theme/colors.dart';
import 'stripe_placeholder.dart';

/// Square 38×38 tone swatch — one of the 7 `StripeTone` values offered by
/// the collection editor (`ink / accent / lime / warn / violet / teal /
/// rose`). Selected state: 2.5px ink ring + bg halo, matching the JSX.
class ToneSwatch extends StatelessWidget {
  const ToneSwatch({
    super.key,
    required this.tone,
    required this.isSelected,
    required this.onTap,
  });

  final StripeTone tone;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 38,
        height: 38,
        decoration: BoxDecoration(
          color: tone.primary,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? NFColors.ink : NFColors.hairline,
            width: isSelected ? 2.5 : 1,
          ),
          boxShadow: isSelected
              ? const [
                  BoxShadow(
                    color: NFColors.bg,
                    blurRadius: 0,
                    spreadRadius: 3,
                  ),
                  BoxShadow(
                    color: NFColors.ink,
                    blurRadius: 0,
                    spreadRadius: 4.5,
                  ),
                ]
              : null,
        ),
      ),
    );
  }
}

/// Maps UI tones (mirroring the JSX `COLLECTION_COLORS` palette) to the
/// underlying `SpaceColor` domain enum — domain models are NOT renamed,
/// only the visual wrapper changes (see P18 spec).
const List<StripeTone> collectionPaletteTones = <StripeTone>[
  StripeTone.ink,
  StripeTone.accent,
  StripeTone.lime,
  StripeTone.warn,
  StripeTone.violet,
  StripeTone.teal,
  StripeTone.rose,
];
