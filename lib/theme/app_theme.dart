import 'package:flutter/material.dart';

/// Emerald + warm ivory brand theme.
class AppTheme {
  AppTheme._();

  static const Color emerald = Color(0xFF0B6E4F);
  static const Color ivory = Color(0xFFFBF7EC);
  static const Color deepGreen = Color(0xFF06261D);

  static ThemeData light([Color seed = emerald, double glass = 0.35]) {
    final scheme = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.light,
    ).copyWith(surface: ivory);
    return _base(scheme, glass);
  }

  static ThemeData dark([Color seed = emerald, double glass = 0.35]) {
    final scheme = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.dark,
    ).copyWith(
      surface: Colors.black,
      surfaceContainerLowest: Colors.black,
      surfaceContainer: const Color(0xFF0C0F0E),
    );
    return _base(scheme, glass);
  }

  static ThemeData _base(ColorScheme scheme, double glass) {
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.transparent,
        elevation: 0,
        indicatorColor: scheme.primary.withValues(alpha: 0.28),
      ),
      appBarTheme: AppBarTheme(
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        backgroundColor: scheme.surface,
        foregroundColor: scheme.onSurface,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.brightness == Brightness.dark
            ? Colors.white.withValues(alpha: 0.04 + glass * 0.1)
            : scheme.primaryContainer.withValues(alpha: glass),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(
            color: (scheme.brightness == Brightness.dark
                    ? Colors.white
                    : scheme.primary)
                .withValues(alpha: 0.14),
          ),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(56),
          textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
    );
  }
}
