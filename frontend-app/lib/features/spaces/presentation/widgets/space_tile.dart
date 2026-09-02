import 'package:flutter/material.dart';
import '../../../../core/sources/domain/space_color.dart';
import '../../../../theme/app_tokens.dart';
import '../../domain/entities/space.dart';

/// A single space row: filled colour dot with halo, name + source count, chevron.
class SpaceTile extends StatelessWidget {
  const SpaceTile({
    super.key,
    required this.space,
    required this.onTap,
    this.onLongPress,
  });

  final Space space;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final SpaceColor sc = space.color;
    final dotColor = sc.accent.fg;

    return InkWell(
      onTap: onTap,
      onLongPress: onLongPress,
      borderRadius: BorderRadius.circular(AppRadii.bmd.topLeft.x),
      child: SizedBox(
        height: 72,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              // Filled dot with zero-blur spread halo
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: dotColor,
                  boxShadow: [
                    BoxShadow(
                      color: dotColor.withValues(alpha: 0.24),
                      blurRadius: 0,
                      spreadRadius: 4,
                    ),
                  ],
                ),
              ),
              SizedBox(width: tokens.spacing.md),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      space.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: tokens.typography.headingS.copyWith(
                        color: tokens.colors.text.primary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${space.sourceCount} источн.',
                      style: tokens.typography.bodyS.copyWith(
                        color: tokens.colors.text.tertiary,
                      ),
                    ),
                  ],
                ),
              ),
              SizedBox(width: tokens.spacing.xs),
              Icon(
                Icons.chevron_right,
                size: 18,
                color: tokens.colors.text.tertiary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
