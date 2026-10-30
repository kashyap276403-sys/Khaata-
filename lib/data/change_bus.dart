import 'package:flutter/foundation.dart';

/// Notifies screens that stored data changed so they can reload.
class ChangeBus extends ChangeNotifier {
  void notifyChanged() => notifyListeners();
}
