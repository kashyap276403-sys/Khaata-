/// Rough Devanagari -> Roman transliteration. It is ONLY used to compare a
/// spoken Hindi name with names saved in Roman letters, to suggest possible
/// customers. The person always confirms the customer.
const Map<String, String> _cons = {
  'क': 'k',
  'ख': 'kh',
  'ग': 'g',
  'घ': 'gh',
  'ङ': 'n',
  'च': 'ch',
  'छ': 'chh',
  'ज': 'j',
  'झ': 'jh',
  'ञ': 'n',
  'ट': 't',
  'ठ': 'th',
  'ड': 'd',
  'ढ': 'dh',
  'ण': 'n',
  'त': 't',
  'थ': 'th',
  'द': 'd',
  'ध': 'dh',
  'न': 'n',
  'प': 'p',
  'फ': 'f',
  'ब': 'b',
  'भ': 'bh',
  'म': 'm',
  'य': 'y',
  'र': 'r',
  'ल': 'l',
  'व': 'v',
  'श': 'sh',
  'ष': 'sh',
  'स': 's',
  'ह': 'h',
};
const Map<String, String> _vowels = {
  'अ': 'a',
  'आ': 'a',
  'इ': 'i',
  'ई': 'i',
  'उ': 'u',
  'ऊ': 'u',
  'ए': 'e',
  'ऐ': 'ai',
  'ओ': 'o',
  'औ': 'au',
  'ऋ': 'ri',
};
const Map<String, String> _matras = {
  'ा': 'a',
  'ि': 'i',
  'ी': 'i',
  'ु': 'u',
  'ू': 'u',
  'े': 'e',
  'ै': 'ai',
  'ो': 'o',
  'ौ': 'au',
  'ृ': 'ri',
};

bool containsDevanagari(String s) =>
    s.runes.any((r) => r >= 0x0900 && r <= 0x097F);

String romanizeDevanagari(String s) {
  final chars = s.split('');
  final out = StringBuffer();
  for (var i = 0; i < chars.length; i++) {
    final ch = chars[i];
    final cons = _cons[ch];
    if (cons != null) {
      out.write(cons);
      if (i + 1 < chars.length) {
        final next = chars[i + 1];
        if (_matras.containsKey(next)) {
          out.write(_matras[next]);
          i++;
        } else if (next == '्') {
          i++; // virama: no vowel
        } else if (next == '़') {
          // nukta: skip it, keep the vowel logic for the following char
          i++;
          if (i + 1 < chars.length && _matras.containsKey(chars[i + 1])) {
            out.write(_matras[chars[i + 1]]);
            i++;
          } else if (i + 1 < chars.length && chars[i + 1] != '्') {
            out.write('a');
          } else if (i + 1 < chars.length) {
            i++;
          }
        } else {
          out.write('a'); // inherent vowel (dropped at the end of a word)
        }
      }
    } else if (_vowels.containsKey(ch)) {
      out.write(_vowels[ch]);
    } else if (ch == 'ं' || ch == 'ँ') {
      out.write('n');
    } else if (ch == 'ः') {
      out.write('h');
    } else if (ch == '़' || ch == '्' || _matras.containsKey(ch)) {
      // stray signs: ignore
    } else {
      out.write(ch);
    }
  }
  return out.toString();
}

/// Devanagari digits -> ASCII digits.
String asciiDigits(String s) {
  final b = StringBuffer();
  for (final r in s.runes) {
    if (r >= 0x0966 && r <= 0x096F) {
      b.write(String.fromCharCode(0x30 + (r - 0x0966)));
    } else {
      b.writeCharCode(r);
    }
  }
  return b.toString();
}
