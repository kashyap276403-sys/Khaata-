import 'package:flutter/material.dart';

/// Plain-language privacy policy. Keep this in sync with PRIVACY.md and with
/// what the app really does.
class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  static const List<(String, String)> _sections = [
    (
      'Aapka data kahan rehta hai',
      'Customers, entries aur history sirf aapke phone ki memory me rehte hain. '
          'Abhi app apna koi data internet par nahi bhejta (sirf voice entry me Android ki speech service, neeche dekhein).',
    ),
    (
      'Awaaz (voice entry)',
      'Bolkar likhne par mic ki ijazat maangi jaati hai. Aapki awaaz Android ki '
          'speech service samajhti hai (aam taur par Google), jo internet ka '
          'istemal kar sakti hai. App awaaz save nahi karta, sirf samjha hua text '
          'dikhata hai. Aap mic ke bina type karke bhi likh sakte hain.',
    ),
    (
      'Hum kya nahi karte',
      'Koi advertisement nahi. Koi subscription ya paid feature nahi. '
          'Koi tracking ya analytics nahi. App aapke customers ko apne aap '
          'koi message ya SMS nahi bhejta.',
    ),
    (
      'Paisa',
      'App sirf hisaab likhta hai. Isse koi payment nahi hoti. '
          'Cash, UPI aur Other sirf record ke liye label hain.',
    ),
    (
      'Share karna',
      'Report ya export tabhi bahar jaata hai jab aap khud share dabate hain, '
          'aur tab aap chunte hain ki kahan bhejna hai. Share se pehle app '
          'chetavni deta hai, kyunki file me customers ke naam aur hisaab hote hain.',
    ),
    (
      'Aapka control',
      'Settings > Data me aap poora data download kar sakte hain, aur poora data '
          'is phone se hamesha ke liye delete bhi kar sakte hain. Delete kiya hua '
          'data wapas nahi aata.',
    ),
    (
      'Phone ki suraksha',
      'App ka apna PIN nahi hai. Phone ka screen lock hi aapke data ki suraksha hai, '
          'isliye phone par screen lock zaroor lagayein.',
    ),
    (
      'Aage aane wale features',
      'Google se login aur Google Drive backup abhi shamil nahi hain. Jab jodenge, '
          'tab backup sirf aapke apne Google account me jayega aur encrypted hoga. '
          'Us waqt yeh page pehle update hoga aur aapse ijazat maangi jayegi.',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Privacy')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          for (final s in _sections) ...[
            Text(s.$1, style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(s.$2, style: theme.textTheme.bodyLarge),
            const SizedBox(height: 16),
          ],
        ],
      ),
    );
  }
}
