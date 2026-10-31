import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';

import '../app.dart' show SettingsScope;

/// Frosted-glass panel: blurs what is behind it, with a soft tinted gradient
/// and a thin light border. Strength follows the Settings "Glass" choice.
class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 20,
    this.highlight = false,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;

  /// Adds an accent-coloured glow (used for the main balance card).
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final dark = scheme.brightness == Brightness.dark;
    double glass = 0.35;
    try {
      glass = SettingsScope.of(context).glassAlpha;
    } catch (_) {}

    final base = dark ? Colors.white : Colors.white;
    final top = base.withValues(
        alpha: dark ? 0.04 + glass * 0.12 : 0.35 + glass * 0.45);
    final bottom =
        base.withValues(alpha: dark ? 0.02 + glass * 0.05 : 0.2 + glass * 0.25);
    final accent = scheme.primary.withValues(alpha: dark ? 0.28 : 0.16);

    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(radius),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: highlight ? [accent, bottom] : [top, bottom],
            ),
            border: Border.all(
              color: (dark ? Colors.white : scheme.primary)
                  .withValues(alpha: dark ? 0.14 : 0.18),
            ),
          ),
          child: child,
        ),
      ),
    );
  }
}

/// Soft coloured glows behind the app so the glass has something to blur.
class AuraBackground extends StatelessWidget {
  const AuraBackground({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final dark = scheme.brightness == Brightness.dark;
    Widget glow(Color c, double size) => Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              colors: [c, c.withValues(alpha: 0)],
            ),
          ),
        );

    return Stack(
      children: [
        Positioned.fill(child: ColoredBox(color: scheme.surface)),
        Positioned(
          top: -120,
          left: -120,
          child:
              glow(scheme.primary.withValues(alpha: dark ? 0.42 : 0.22), 420),
        ),
        Positioned(
          bottom: -140,
          right: -140,
          child:
              glow(scheme.tertiary.withValues(alpha: dark ? 0.30 : 0.16), 460),
        ),
        Positioned.fill(child: child),
      ],
    );
  }
}
