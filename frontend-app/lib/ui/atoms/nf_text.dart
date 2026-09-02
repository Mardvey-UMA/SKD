import 'package:flutter/widgets.dart';

import '../../responsive/breakpoint.dart';
import '../../responsive/context_ext.dart';
import '../../theme/colors.dart';
import '../../theme/typography.dart';

/// Neo-futurism text atom.
///
/// Named constructors layer on top of [NFTypography] and produce a single
/// [Text] widget. `mono` uppercases the value internally so call-sites never
/// need `.toUpperCase()` (see `Do NOT` rule in Prompt 04).
///
/// `display` resolves a responsive size (38/42/46) and letter-spacing
/// (−1.2/−1.4/−1.6) — either from an explicit [Breakpoint] argument or from
/// `context.breakpoint`.
class NFText extends StatelessWidget {
  const NFText._({
    super.key,
    required this.data,
    required _StyleToken? baseStyle,
    required this.uppercase,
    required this.resolveDisplay,
    this.breakpoint,
    this.color,
    this.textAlign,
    this.maxLines,
    this.overflow,
  }) : _baseStyle = baseStyle;

  /// Hero / display — 38/42/46 · w800 · −1.2/−1.4/−1.6.
  ///
  /// If [breakpoint] is omitted the widget reads `context.breakpoint` at
  /// build time.
  const NFText.display(
    String data, {
    Key? key,
    Breakpoint? breakpoint,
    Color? color,
    TextAlign? textAlign,
    int? maxLines,
    TextOverflow? overflow,
  }) : this._(
          key: key,
          data: data,
          baseStyle: null,
          uppercase: false,
          resolveDisplay: true,
          breakpoint: breakpoint,
          color: color,
          textAlign: textAlign,
          maxLines: maxLines,
          overflow: overflow,
        );

  /// H1 — 22 · w700 · −0.6.
  const NFText.h1(
    String data, {
    Key? key,
    Color? color,
    TextAlign? textAlign,
    int? maxLines,
    TextOverflow? overflow,
  }) : this._(
          key: key,
          data: data,
          baseStyle: _StyleToken.h1,
          uppercase: false,
          resolveDisplay: false,
          color: color,
          textAlign: textAlign,
          maxLines: maxLines,
          overflow: overflow,
        );

  /// H2 — 21 · w600 · −0.5.
  const NFText.h2(
    String data, {
    Key? key,
    Color? color,
    TextAlign? textAlign,
    int? maxLines,
    TextOverflow? overflow,
  }) : this._(
          key: key,
          data: data,
          baseStyle: _StyleToken.h2,
          uppercase: false,
          resolveDisplay: false,
          color: color,
          textAlign: textAlign,
          maxLines: maxLines,
          overflow: overflow,
        );

  /// Body — 14.5 · w400 · 1.5 line-height.
  const NFText.body(
    String data, {
    Key? key,
    Color? color,
    TextAlign? textAlign,
    int? maxLines,
    TextOverflow? overflow,
  }) : this._(
          key: key,
          data: data,
          baseStyle: _StyleToken.body,
          uppercase: false,
          resolveDisplay: false,
          color: color,
          textAlign: textAlign,
          maxLines: maxLines,
          overflow: overflow,
        );

  /// Meta — 13 · w600.
  const NFText.meta(
    String data, {
    Key? key,
    Color? color,
    TextAlign? textAlign,
    int? maxLines,
    TextOverflow? overflow,
  }) : this._(
          key: key,
          data: data,
          baseStyle: _StyleToken.meta,
          uppercase: false,
          resolveDisplay: false,
          color: color,
          textAlign: textAlign,
          maxLines: maxLines,
          overflow: overflow,
        );

  /// Mono — 10 · w700 · letter-spacing 0.8 · uppercased.
  ///
  /// Value is uppercased at the widget layer, so call-sites must NOT call
  /// `.toUpperCase()`. Color defaults to [NFColors.mute] and is overridable.
  const NFText.mono(
    String data, {
    Key? key,
    Color? color,
    TextAlign? textAlign,
    int? maxLines,
    TextOverflow? overflow,
  }) : this._(
          key: key,
          data: data,
          baseStyle: _StyleToken.mono,
          uppercase: true,
          resolveDisplay: false,
          color: color,
          textAlign: textAlign,
          maxLines: maxLines,
          overflow: overflow,
        );

  final String data;
  final _StyleToken? _baseStyle;
  final bool uppercase;
  final bool resolveDisplay;
  final Breakpoint? breakpoint;
  final Color? color;
  final TextAlign? textAlign;
  final int? maxLines;
  final TextOverflow? overflow;

  @override
  Widget build(BuildContext context) {
    final TextStyle style = resolveDisplay
        ? _displayStyle(breakpoint ?? context.breakpoint)
        : _tokenStyle(_baseStyle!);

    final TextStyle effective =
        color != null ? style.copyWith(color: color) : style;

    return Text(
      uppercase ? data.toUpperCase() : data,
      style: effective,
      textAlign: textAlign,
      maxLines: maxLines,
      overflow: overflow,
    );
  }

  static TextStyle _tokenStyle(_StyleToken token) {
    switch (token) {
      case _StyleToken.h1:
        return NFTypography.h1;
      case _StyleToken.h2:
        return NFTypography.h2;
      case _StyleToken.body:
        return NFTypography.body;
      case _StyleToken.meta:
        return NFTypography.meta;
      case _StyleToken.mono:
        return NFTypography.mono;
    }
  }

  static TextStyle _displayStyle(Breakpoint bp) {
    switch (bp) {
      case Breakpoint.mobile:
        return NFTypography.display
            .copyWith(fontSize: 38, letterSpacing: -1.2);
      case Breakpoint.tablet:
        return NFTypography.display
            .copyWith(fontSize: 42, letterSpacing: -1.4);
      case Breakpoint.desktop:
        return NFTypography.display
            .copyWith(fontSize: 46, letterSpacing: -1.6);
    }
  }
}

enum _StyleToken { h1, h2, body, meta, mono }
