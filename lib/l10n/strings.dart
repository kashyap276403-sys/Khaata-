import 'package:flutter/widgets.dart';

import '../app.dart' show SettingsScope;

/// App languages. Hinglish is the default (Hindi in Roman letters).
enum AppLanguage { hinglish, english }

/// Only the screens listed in README ("Language") use this so far; the rest
/// of the app is still Hinglish.
const Map<String, (String, String)> _strings = {
  'nav.home': ('Home', 'Home'),
  'nav.customers': ('Customers', 'Customers'),
  'nav.quick': ('Quick Entry', 'Quick Entry'),
  'nav.reports': ('Reports', 'Reports'),
  'nav.settings': ('Settings', 'Settings'),
  'home.greeting': (
    'Namaste! Aapka digital khaata.',
    'Hello! Your digital ledger.'
  ),
  'home.outstanding': ('Total baaki (Outstanding)', 'Total outstanding'),
  'home.todayUdhaari': ('Aaj ka udhaari', "Today's credit given"),
  'home.todayJama': ('Aaj ka jama', "Today's payments received"),
  'home.customers': ('Total customers', 'Total customers'),
  'home.pending': ('Baaki wale', 'With balance due'),
  'home.udhaari': ('Udhaari', 'Credit given (Udhaari)'),
  'home.jama': ('Jama', 'Payment received (Jama)'),
  'home.history': ('History', 'History'),
  'home.trash': ('Trash', 'Trash'),
  'home.recent': ('Recent transactions', 'Recent transactions'),
  'home.empty': (
    'Abhi koi transaction nahi. Customers tab me customer chunkar Udhaari ya Jama likhein.',
    'No transactions yet. Pick a customer in the Customers tab and add Udhaari or Jama.'
  ),
  'home.dueSub': ('Dekhne ke liye dabayein', 'Tap to view'),
  'settings.language': ('Bhasha (Language)', 'Language'),
  'settings.langNote': (
    'Abhi Home aur neeche ka menu badalta hai; baaki screens abhi Hinglish me hain.',
    'Currently this changes Home and the bottom menu; other screens are still Hinglish.'
  ),
};

/// Looks up [key] in the current language. Unknown keys return the key itself
/// so a typo is visible instead of crashing.
String tr(BuildContext context, String key) {
  final entry = _strings[key];
  if (entry == null) return key;
  var english = false;
  try {
    english = SettingsScope.of(context).language == AppLanguage.english;
  } catch (_) {}
  return english ? entry.$2 : entry.$1;
}

String trDue(BuildContext context, int count) {
  var english = false;
  try {
    english = SettingsScope.of(context).language == AppLanguage.english;
  } catch (_) {}
  return english
      ? '$count customer(s) have reached their due date'
      : '$count customer ki wapsi ki tareekh aa gayi';
}
