import 'package:flutter/material.dart';
import 'icon_tile.dart';
import '../../theme/app_tokens.dart';

class ProfileListItem extends StatelessWidget {
  const ProfileListItem({
    super.key,
    required this.iconVariant,
    required this.icon,
    required this.title,
    this.subtitle,
    this.trailing,
    this.destructive = false,
    this.onTap,
  });

  final AccentVariant iconVariant;
  final IconData icon;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final bool destructive;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;

    final titleColor = destructive
        ? tokens.colors.status.error
        : tokens.colors.text.primary;

    Widget iconWidget = destructive
        ? Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.errorBg,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, size: 20, color: AppColors.error),
          )
        : IconTile(
            icon: icon,
            size: 40,
            iconSize: 20,
            variant: iconVariant,
            borderRadius: 12,
          );

    Widget? trailingWidget;
    if (destructive) {
      trailingWidget = null;
    } else if (trailing != null) {
      trailingWidget = trailing;
    } else if (onTap != null) {
      trailingWidget = Icon(
        Icons.chevron_right,
        size: 18,
        color: tokens.colors.text.tertiary,
      );
    }

    final hasSubtitle = subtitle != null && subtitle!.isNotEmpty;

    return InkWell(
      onTap: onTap,
      borderRadius: AppRadii.blg,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 72),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              iconWidget,
              SizedBox(width: tokens.spacing.md),
              Expanded(
                child: hasSubtitle
                    ? Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: tokens.typography.headingS.copyWith(
                              color: titleColor,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            subtitle!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: tokens.typography.bodyS.copyWith(
                              color: tokens.colors.text.tertiary,
                            ),
                          ),
                        ],
                      )
                    : Text(
                        title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: tokens.typography.headingS.copyWith(
                          color: titleColor,
                        ),
                      ),
              ),
              if (trailingWidget != null) ...[
                SizedBox(width: tokens.spacing.xs),
                trailingWidget,
              ],
            ],
          ),
        ),
      ),
    );
  }
}
