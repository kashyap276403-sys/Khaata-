import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Look-and-feel preferences, saved on the phone.
class AppSettings extends ChangeNotifier {
  AppSettings._(
      this._prefs, this._themeMode, this._accentIndex, this._fontIndex);

  /// Keeps values in memory only (tests, or when storage is unavailable).
  factory AppSettings.memory() => AppSettings._(null, ThemeMode.system, 0, 1);

  /// Loads saved values; falls back to defaults if storage fails.
  static Future<AppSettings> load() async {
    try {
      final p = await SharedPreferences.getInstance();
      final mode = p.getInt(_kTheme) ?? ThemeMode.system.index;
      final accent = p.getInt(_kAccent) ?? 0;
      final font = p.getInt(_kFont) ?? 1;
      return AppSettings._(
        p,
        ThemeMode.values[mode.clamp(0, ThemeMode.values.length - 1)],
        accent.clamp(0, accentColors.length - 1),
        font.clamp(0, fontScales.length - 1),
      );
    } catch (_) {
      return AppSettings.memory();
    }
  }

  static const String _kTheme = 'theme_mode';
  static const String _kAccent = 'accent_index';
  static const String _kFont = 'font_index';

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

  final SharedPreferences? _prefs;
  ThemeMode _themeMode;
  int _accentIndex;
  int _fontIndex;

  ThemeMode get themeMode => _themeMode;
  int get accentIndex => _accentIndex;
  int get fontIndex => _fontIndex;
  Color get accent => accentColors[_accentIndex];
  double get fontScale => fontScales[_fontIndex];

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
