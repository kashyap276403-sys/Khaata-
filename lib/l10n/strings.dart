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
  'cust.new': ('Naya customer', 'New customer'),
  'cust.search': (
    'Naam, phone ya ID se khojein',
    'Search by name, phone or ID'
  ),
  'cust.active': ('Active', 'Active'),
  'cust.archived': ('Archived', 'Archived'),
  'cust.loadFail': ('Load nahi hua: ', 'Could not load: '),
  'cust.noMatch': ('Koi customer nahi mila.', 'No customer found.'),
  'cust.emptyArchive': (
    'Archive me koi customer nahi.\nCustomer ke menu se "Archive" karne par wo yahan aayega.',
    'No archived customers.\nCustomers you archive from their menu will appear here.'
  ),
  'cust.empty': (
    'Abhi koi customer nahi.\n"Naya customer" dabakar shuru karein.',
    'No customers yet.\nTap "New customer" to begin.'
  ),
  'cust.due': ('Baaki', 'Due'),
  'cust.advance': ('Advance', 'Advance'),
  'cust.settled': ('Barabar', 'Settled'),
  'txn.udhaari': ('Udhaari', 'Credit (Udhaari)'),
  'txn.jama': ('Jama', 'Payment (Jama)'),
  'q.voice': ('Bolkar likhein', 'Speak to enter'),
  'q.repeat': ('Pichli entry dobara: ', 'Repeat last entry: '),
  'q.review': (
    'Save se pehle check hoga.',
    'You will review it before saving.'
  ),
  'q.search': (
    'Customer khojein (naam, phone, ID)',
    'Search customer (name, phone, ID)'
  ),
  'q.recent': ('Haal ke customers', 'Recent customers'),
  'q.results': ('Khoj ke nateeje', 'Search results'),
  'q.emptyRecent': (
    'Abhi koi haal ka customer nahi. Upar khoj me naam likhein, ya Customers tab me naya customer banayein.',
    'No recent customers yet. Search by name above, or add a new customer in the Customers tab.'
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
