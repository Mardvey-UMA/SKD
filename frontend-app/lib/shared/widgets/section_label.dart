import 'package:flutter/material.dart';
import '../../theme/app_tokens.dart';

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key, this.uppercase = true});

  final String text;
  final bool uppercase;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final label = uppercase ? text.toUpperCase() : text;

    return Padding(
      padding: EdgeInsets.only(
        top: tokens.spacing.xl2,
        bottom: tokens.spacing.sm,
      ),
      child: Text(
        label,
        style: tokens.typography.overline.copyWith(
          color: tokens.colors.text.tertiary,
        ),
      ),
    );
  }
}
