import 'package:flutter/material.dart';

import 'app_tokens.dart';

export 'app_tokens.dart';

/// BuildContext extensions for convenient token access.
///
/// Usage:
///   context.tokens.colors.interactive.primary
///   context.colors.text.primary
///   context.typography.headingL
///   context.spacing.lg
extension ThemeX on BuildContext {
  /// Full token tree — all namespaces.
  AppTokens get tokens => Theme.of(this).extension<AppTokens>()!;

  /// Shorthand for `tokens.colors`.
  TokenColors get colors => tokens.colors;

  /// Shorthand for `tokens.typography`.
  TokenTypography get typography => tokens.typography;

  /// Shorthand for `tokens.spacing`.
  TokenSpacing get spacing => tokens.spacing;

  /// Shorthand for `tokens.radii`.
  TokenRadii get radii => tokens.radii;

  /// Shorthand for `tokens.elevation`.
  TokenElevation get elevation => tokens.elevation;

  /// Shorthand for `tokens.gradients`.
  TokenGradients get gradients => tokens.gradients;

  /// Shorthand for `tokens.motion`.
  TokenMotion get motion => tokens.motion;
}
