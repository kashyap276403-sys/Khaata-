import '../core/money.dart';
import '../core/text_utils.dart';
import '../data/models.dart';
import 'devanagari.dart';

/// What the app understood from a spoken or typed sentence.
///
/// Customer, amount, type and payment method are found separately. Nothing
/// here saves anything: the screen always shows this as a preview and asks
/// for confirmation. Anything unclear is left empty (null / no candidates)
/// and listed in [issues] instead of being guessed.
class VoiceCommand {
  const VoiceCommand({
    required this.transcript,
    this.type,
    this.amountPaise,
    this.method,
    this.customerQuery = '',
    this.candidates = const [],
    this.issues = const [],
  });

  final String transcript;
  final TxnType? type;
  final int? amountPaise;

  /// Only set when a method was actually spoken.
  final PayMethod? method;

  /// The part of the sentence taken as the customer name.
  final String customerQuery;

  /// Possible customers. Exactly one = a likely match (still confirmed on the
  /// preview); several = the person must choose; none = choose manually.
  final List<Customer> candidates;
  final List<String> issues;
}

const Set<String> _currency = {
  'rupaye', 'rupaya', 'rupay', 'rupees', 'rupee', 'rs', 'inr', 'rupye',
  'रुपये', 'रुपया', 'रुपए', 'रूपये', 'रु',
};

const Set<String> _udhaariWords = {
  'udhaari', 'udhari', 'udhaar', 'udhar', 'credit', 'loan',
  'उधार', 'उधारी',
};

const Set<String> _jamaWords = {
  'jama', 'jamaa', 'jmaa', 'paid', 'pay', 'payment', 'received', 'receive',
  'chukaya', 'chukaye', 'wapas', 'bhara', 'collection',
  'जमा', 'चुकाया', 'चुकाए', 'वापस', 'भरा',
};

const Map<String, PayMethod> _methodWords = {
  'cash': PayMethod.cash, 'nakad': PayMethod.cash, 'nakdi': PayMethod.cash,
  'कैश': PayMethod.cash, 'नकद': PayMethod.cash,
  'upi': PayMethod.upi, 'phonepe': PayMethod.upi, 'phonepay': PayMethod.upi,
  'gpay': PayMethod.upi, 'googlepay': PayMethod.upi, 'paytm': PayMethod.upi,
  'bhim': PayMethod.upi, 'online': PayMethod.upi,
  'यूपीआई': PayMethod.upi, 'यूपीआइ': PayMethod.upi,
  'other': PayMethod.other, 'anya': PayMethod.other, 'अन्य': PayMethod.other,
};

const Set<String> _fillers = {
  'ke', 'ki', 'ka', 'ko', 'ne', 'se', 'me', 'mein', 'par', 'pe', 'hai',
  'hain', 'kar', 'karo', 'kiye', 'kiya', 'kare', 'karna', 'kijiye', 'likho',
  'likh', 'likhna', 'likhdo', 'add', 'enter', 'entry', 'for', 'to', 'of',
  'by', 'in', 'on', 'the', 'a', 'an', 'via', 'through', 'with', 'diye',
  'diya', 'de', 'dena', 'do', 'aaj', 'today', 'ji', 'and', 'aur', 'please',
  'के', 'की', 'का', 'को', 'ने', 'से', 'में', 'पर', 'है', 'हैं', 'कर',
  'करो', 'किए', 'किया', 'करें', 'लिखो', 'दे', 'दो', 'दिए', 'दिया', 'आज',
  'जी', 'और',
};

const Map<String, int> _numberWords = {
  'ek': 1, 'do': 2, 'teen': 3, 'char': 4, 'chaar': 4, 'paanch': 5,
  'panch': 5, 'chhe': 6, 'chhah': 6, 'che': 6, 'saat': 7, 'aath': 8,
  'nau': 9, 'das': 10, 'gyarah': 11, 'barah': 12, 'terah': 13,
  'chaudah': 14, 'pandrah': 15, 'solah': 16, 'satrah': 17, 'atharah': 18,
  'unnis': 19, 'bees': 20, 'tees': 30, 'chalis': 40, 'pachas': 50,
  'sattar': 70, 'assi': 80, 'nabbe': 90,
  'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5, 'six': 6,
  'seven': 7, 'eight': 8, 'nine': 9, 'ten': 10, 'eleven': 11, 'twelve': 12,
  'thirteen': 13, 'fourteen': 14, 'fifteen': 15, 'sixteen': 16,
  'seventeen': 17, 'eighteen': 18, 'nineteen': 19, 'twenty': 20,
  'thirty': 30, 'forty': 40, 'fifty': 50, 'sixty': 60, 'seventy': 70,
  'eighty': 80, 'ninety': 90,
  'एक': 1, 'दो': 2, 'तीन': 3, 'चार': 4, 'पांच': 5, 'पाँच': 5, 'छह': 6,
  'छः': 6, 'सात': 7, 'आठ': 8, 'नौ': 9, 'दस': 10, 'बीस': 20, 'तीस': 30,
  'चालीस': 40, 'पचास': 50, 'सत्तर': 70, 'अस्सी': 80, 'नब्बे': 90,
};

const Map<String, int> _multipliers = {
  'sau': 100, 'hundred': 100, 'सौ': 100,
  'hazaar': 1000, 'hazar': 1000, 'thousand': 1000, 'हज़ार': 1000, 'हजार': 1000,
  'lakh': 100000, 'लाख': 100000,
};

/// 1.5 and 2.5 ("dedh sau", "dhai hazaar"), kept as halves.
const Map<String, int> _halfWords = {
  'dedh': 3, 'dhai': 5, 'डेढ़': 3, 'ढाई': 5,
};

final RegExp _digitToken = RegExp(r'^\d+(\.\d{1,2})?$');

enum _Kind { digit, word, multiplier, half, other }

_Kind _kindOf(String t) {
  if (_digitToken.hasMatch(t)) return _Kind.digit;
  if (_numberWords.containsKey(t)) return _Kind.word;
  if (_multipliers.containsKey(t)) return _Kind.multiplier;
  if (_halfWords.containsKey(t)) return _Kind.half;
  return _Kind.other;
}

class _Run {
  _Run(this.start, this.end, this.paise);
  final int start; // inclusive token index
  final int end; // inclusive token index
  final int paise;
}

List<String> _tokenize(String text) {
  var t = asciiDigits(text.toLowerCase());
  t = t.replaceAll('₹', ' rs ');
  // "1,000" -> "1000"
  t = t.replaceAllMapped(RegExp(r'(?<=\d),(?=\d{3}(\D|$))'), (_) => '');
  t = t.replaceAll(RegExp(r'[^\p{L}\p{M}\p{N}.]+', unicode: true), ' ');
  final out = <String>[];
  for (var tok in t.split(' ')) {
    tok = tok.replaceAll(RegExp(r'^\.+|\.+$'), '');
    if (tok.isNotEmpty) out.add(tok);
  }
  return out;
}

/// Finds amounts in the token list. Number words (paanch sau, do hazaar) are
/// only trusted when they contain sau/hazaar/lakh or are followed by a
/// currency word, so ordinary words like "do" (give) are not read as 2.
List<_Run> _findRuns(List<String> tokens) {
  final runs = <_Run>[];
  var i = 0;
  while (i < tokens.length) {
    final k = _kindOf(tokens[i]);
    if (k == _Kind.other) {
      i++;
      continue;
    }
    var j = i;
    while (j + 1 < tokens.length && _kindOf(tokens[j + 1]) != _Kind.other) {
      j++;
    }
    final slice = tokens.sublist(i, j + 1);
    final kinds = slice.map(_kindOf).toList();
    final followedByCurrency =
        j + 1 < tokens.length && _currency.contains(tokens[j + 1]);
    final hasMultiplier = kinds.contains(_Kind.multiplier);
    final hasWords = kinds.any((x) => x == _Kind.word || x == _Kind.half);

    int? paise;
    if (slice.length == 1 && kinds.first == _Kind.digit) {
      final digits = slice.first.split('.').first;
      if (digits.length <= 9) paise = parseRupeesToPaise(slice.first);
    } else if (hasMultiplier || (hasWords && followedByCurrency)) {
      paise = _evaluate(slice);
    }
    if (paise != null && paise > 0) {
      runs.add(_Run(i, j, paise));
    }
    i = j + 1;
  }
  return runs;
}

/// Evaluates a run such as [do, hazaar, paanch, sau] or [5, sau].
/// Works in half-rupees so "dedh sau" (150) stays exact; no floating point.
int? _evaluate(List<String> slice) {
  var total2 = 0;
  var cur2 = 0;
  for (final t in slice) {
    final k = _kindOf(t);
    switch (k) {
      case _Kind.digit:
        if (t.contains('.')) return null;
        final d = int.tryParse(t);
        if (d == null) return null;
        cur2 += d * 2;
      case _Kind.word:
        cur2 += _numberWords[t]! * 2;
      case _Kind.half:
        cur2 += _halfWords[t]!;
      case _Kind.multiplier:
        final m = _multipliers[t]!;
        final base2 = cur2 == 0 ? 2 : cur2;
        if (m == 100) {
          cur2 = base2 * 100;
        } else {
          total2 += base2 * m;
          cur2 = 0;
        }
      case _Kind.other:
        return null;
    }
  }
  total2 += cur2;
  final paise = total2 * 50; // each half-rupee = 50 paise
  if (paise > maxAmountPaise) return null;
  return paise;
}

bool _tokenMatches(String ct, String nt) {
  if (ct == nt) return true;
  if (ct.length >= 3 && nt.startsWith(ct)) return true;
  final a = nameSkeleton(ct);
  final b = nameSkeleton(nt);
  return a.length >= 2 && a == b;
}

String _romanToken(String t) =>
    containsDevanagari(t) ? romanizeDevanagari(t) : t;

/// Customers that the spoken name could mean, best matches only.
List<Customer> matchCustomers(String query, List<Customer> customers) {
  final q = normalizeName(query);
  if (q.isEmpty) return const [];
  final qTokens = q.split(' ').where((t) => t.isNotEmpty).toList();

  final exact = <Customer>[];
  final tokenExact = <Customer>[];
  final fuzzy = <Customer>[];
  for (final c in customers) {
    final n = normalizeName(c.name);
    if (n == q) {
      exact.add(c);
      continue;
    }
    final nTokens = n.split(' ').where((t) => t.isNotEmpty).toList();
    if (qTokens.every(nTokens.contains)) {
      tokenExact.add(c);
      continue;
    }
    final qr = qTokens.map(_romanToken).toList();
    final nr = nTokens.map(_romanToken).toList();
    if (qr.every((qt) => nr.any((nt) => _tokenMatches(qt, nt)))) {
      fuzzy.add(c);
    }
  }
  if (exact.isNotEmpty) return exact;
  if (tokenExact.isNotEmpty) return tokenExact;
  return fuzzy;
}

/// Understands sentences like:
///   "Ramesh ke 500 rupaye udhaari"
///   "Suresh ne 300 rupaye UPI se jama kiye"
///   "Mohan ke 200 rupaye cash jama karo"
VoiceCommand parseVoiceCommand(
  String transcript, {
  required List<Customer> customers,
}) {
  final issues = <String>[];
  final tokens = _tokenize(transcript);
  if (tokens.isEmpty) {
    return VoiceCommand(
      transcript: transcript,
      issues: const ['Kuch suna nahi gaya. Dobara bolein ya type karein.'],
    );
  }

  // --- amount
  final runs = _findRuns(tokens);
  int? amount;
  final usedIdx = <int>{};
  if (runs.isEmpty) {
    issues.add('Amount samajh nahi aaya.');
  } else if (runs.map((r) => r.paise).toSet().length > 1) {
    issues.add('Kai amount sunai diye. Sahi amount khud likhein.');
  } else {
    amount = runs.first.paise;
  }
  for (final r in runs) {
    for (var i = r.start; i <= r.end; i++) {
      usedIdx.add(i);
    }
  }

  // --- type and method
  var hasUdhaari = false;
  var hasJama = false;
  final methods = <PayMethod>{};
  for (var i = 0; i < tokens.length; i++) {
    if (usedIdx.contains(i)) continue;
    final t = tokens[i];
    if (_udhaariWords.contains(t)) {
      hasUdhaari = true;
      usedIdx.add(i);
    } else if (_jamaWords.contains(t)) {
      hasJama = true;
      usedIdx.add(i);
    } else if (_methodWords.containsKey(t)) {
      methods.add(_methodWords[t]!);
      usedIdx.add(i);
    } else if (_currency.contains(t)) {
      usedIdx.add(i);
    }
  }
  TxnType? type;
  if (hasUdhaari && hasJama) {
    issues.add('Udhaari aur Jama dono sunai diye. Ek chunein.');
  } else if (hasUdhaari) {
    type = TxnType.udhaari;
  } else if (hasJama) {
    type = TxnType.jama;
  } else {
    issues.add('Udhaari hai ya Jama? Batayein.');
  }
  PayMethod? method;
  if (methods.length > 1) {
    issues.add('Kai payment tareeke sunai diye. Ek chunein.');
  } else if (methods.length == 1) {
    method = methods.first;
  }

  // --- customer: whatever is left after removing the parts above
  final nameTokens = <String>[];
  for (var i = 0; i < tokens.length; i++) {
    if (usedIdx.contains(i)) continue;
    if (_fillers.contains(tokens[i])) continue;
    nameTokens.add(tokens[i]);
  }
  final query = nameTokens.join(' ');
  var candidates = const <Customer>[];
  if (query.isEmpty) {
    issues.add('Customer ka naam nahi suna.');
  } else {
    candidates = matchCustomers(query, customers);
    if (candidates.isEmpty) {
      issues.add('"$query" naam ka customer nahi mila. Neeche se chunein.');
    } else if (candidates.length > 1) {
      issues.add('Is naam ke ek se zyada customer hain. Sahi wala chunein.');
    }
  }

  return VoiceCommand(
    transcript: transcript,
    type: type,
    amountPaise: amount,
    method: method,
    customerQuery: query,
    candidates: candidates,
    issues: issues,
  );
}
