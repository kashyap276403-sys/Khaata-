import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/strings.dart';

/// Look-and-feel and mode preferences, saved on the phone.
class AppSettings extends ChangeNotifier {
  AppSettings._(this._prefs, this._themeMode, this._accentIndex,
      this._fontIndex, this._glassIndex, this._proMode,
      [this._language = AppLanguage.hinglish]);

  /// Keeps values in memory only (tests, or when storage is unavailable).
  factory AppSettings.memory() =>
      AppSettings._(null, ThemeMode.dark, 0, 1, 1, true);

  /// Loads saved values; falls back to defaults if storage fails.
  static Future<AppSettings> load() async {
    try {
      final p = await SharedPreferences.getInstance();
      final mode = p.getInt(_kTheme) ?? ThemeMode.dark.index;
      final accent = p.getInt(_kAccent) ?? 0;
      final font = p.getInt(_kFont) ?? 1;
      final glass = p.getInt(_kGlass) ?? 1;
      return AppSettings._(
        p,
        ThemeMode.values[mode.clamp(0, ThemeMode.values.length - 1)],
        accent.clamp(0, accentColors.length - 1),
        font.clamp(0, fontScales.length - 1),
        glass.clamp(0, glassAlphas.length - 1),
        p.getBool(_kPro) ?? true,
        (p.getInt(_kLang) ?? 0) == 1
            ? AppLanguage.english
            : AppLanguage.hinglish,
      );
    } catch (_) {
      return AppSettings.memory();
    }
  }

  static const String _kTheme = 'theme_mode';
  static const String _kAccent = 'accent_index';
  static const String _kFont = 'font_index';
  static const String _kGlass = 'glass_index';
  static const String _kPro = 'pro_mode';
  static const String _kLang = 'language';

  /// Accent choices (first is the brand emerald).
  static const List<Color> accentColors = [
    Color(0xFF0B6E4F),
    Color(0xFF1565C0),
    Color(0xFF6A1B9A),
    Color(0xFFE65100),
    Color(0xFF8E0000),
  ];
  static const List<String> accentNames = [
    'Emerald',
    'Neela',
    'Jamuni',
    'Narangi',
    'Maroon',
  ];

  static const List<double> fontScales = [0.9, 1.0, 1.15, 1.3];
  static const List<String> fontNames = [
    'Chhota',
    'Normal',
    'Bada',
    'Bahut bada',
  ];

  /// How see-through cards are (higher = more solid tint).
  static const List<double> glassAlphas = [0.15, 0.35, 0.55, 0.75];
  static const List<String> glassNames = [
    'Kam',
    'Madhyam',
    'Zyada',
    'Bahut zyada',
  ];

  final SharedPreferences? _prefs;
  ThemeMode _themeMode;
  int _accentIndex;
  int _fontIndex;
  int _glassIndex;
  bool _proMode;
  AppLanguage _language;

  AppLanguage get language => _language;
  ThemeMode get themeMode => _themeMode;
  int get accentIndex => _accentIndex;
  int get fontIndex => _fontIndex;
  int get glassIndex => _glassIndex;
  Color get accent => accentColors[_accentIndex];
  double get fontScale => fontScales[_fontIndex];
  double get glassAlpha => glassAlphas[_glassIndex];

  /// Pro mode shows every detail. Simple mode hides the extra figures and
  /// advanced tools so the main screens stay uncluttered.
  bool get proMode => _proMode;

  Future<void> setThemeMode(ThemeMode m) async {
    _themeMode = m;
    notifyListeners();
    await _safe((p) => p.setInt(_kTheme, m.index));
  }

  Future<void> setAccent(int i) async {
    _accentIndex = i.clamp(0, accentColors.length - 1);
    notifyListeners();
    await _safe((p) => p.setInt(_kAccent, _accentIndex));
  }

  Future<void> setFontIndex(int i) async {
    _fontIndex = i.clamp(0, fontScales.length - 1);
    notifyListeners();
    await _safe((p) => p.setInt(_kFont, _fontIndex));
  }

  Future<void> setGlassIndex(int i) async {
    _glassIndex = i.clamp(0, glassAlphas.length - 1);
    notifyListeners();
    await _safe((p) => p.setInt(_kGlass, _glassIndex));
  }

  Future<void> setLanguage(AppLanguage l) async {
    _language = l;
    notifyListeners();
    await _safe((p) => p.setInt(_kLang, l.index));
  }

  Future<void> setProMode(bool v) async {
    _proMode = v;
    notifyListeners();
    await _safe((p) => p.setBool(_kPro, v));
  }

  Future<void> _safe(Future<bool> Function(SharedPreferences p) write) async {
    final p = _prefs;
    if (p == null) return;
    try {
      await write(p);
    } catch (_) {
      // The setting still applies for this session if saving fails.
    }
  }
}
