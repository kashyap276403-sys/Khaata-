import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../core/money.dart';
import '../core/text_utils.dart';
import '../data/models.dart';
import '../services.dart';
import '../voice/voice_parser.dart';
import '../widgets/txn_tile.dart';

/// Voice (or typed) entry. The sentence is turned into a PREVIEW; nothing is
/// saved until the person checks it and presses "Confirm aur Save".
/// Typing the sentence, or filling the entry by hand, always works.
class VoiceEntryScreen extends StatefulWidget {
  const VoiceEntryScreen({super.key});

  @override
  State<VoiceEntryScreen> createState() => _VoiceEntryScreenState();
}

class _VoiceEntryScreenState extends State<VoiceEntryScreen> {
  final TextEditingController _transcript = TextEditingController();
  final TextEditingController _amount = TextEditingController();
  final TextEditingController _search = TextEditingController();

  Future<void>? _loading;
  List<Customer> _all = const [];

  String _locale = 'hi_IN';
  bool _listening = false;
  String? _speechMessage;

  VoiceCommand? _cmd;
  TxnType? _type;
  PayMethod _method = PayMethod.cash;
  List<Customer> _candidates = const [];
  Customer? _picked;
  String _key = const Uuid().v4();
  bool _saving = false;
  String? _error;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _loading ??= _loadCustomers();
  }

  Future<void> _loadCustomers() async {
    final services = ServicesScope.of(context);
    final list = await services.customers.list();
    if (!mounted) return;
    setState(() => _all = list.map((e) => e.customer).toList());
  }

  @override
  void dispose() {
    _transcript.dispose();
    _amount.dispose();
    _search.dispose();
    super.dispose();
  }

  Future<void> _toggleListening() async {
    final services = ServicesScope.of(context);
    if (_listening) {
      await services.speech.stop();
      if (mounted) setState(() => _listening = false);
      _parse();
      return;
    }
    setState(() => _speechMessage = null);
    final ok = await services.speech.initialize((m) {
      if (mounted) setState(() => _speechMessage = 'Awaaz pehchanne me dikkat: $m');
    });
    if (!mounted) return;
    if (!ok) {
      setState(() => _speechMessage =
          'Microphone ki ijazat nahi mili ya is phone par speech service '
          'available nahi hai. Aap neeche type karke bhi likh sakte hain.');
      return;
    }
    setState(() => _listening = true);
    try {
      await services.speech.start(
        localeId: _locale,
        onText: (t) {
          if (mounted) setState(() => _transcript.text = t);
        },
        onDone: () {
          if (!mounted || !_listening) return;
          setState(() => _listening = false);
          _parse();
        },
      );
    } catch (e) {
      if (mounted) {
        setState(() {
          _listening = false;
          _speechMessage = 'Sunna shuru nahi ho paya: $e';
        });
      }
    }
  }

  void _parse() {
    final cmd = parseVoiceCommand(_transcript.text, customers: _all);
    setState(() {
      _cmd = cmd;
      _type = cmd.type;
      _amount.text =
          cmd.amountPaise == null ? '' : paiseToInput(cmd.amountPaise!);
      _method = cmd.method ?? PayMethod.cash;
      _candidates = cmd.candidates;
      // Only a single clear match is pre-selected; several = person chooses.
      _picked = cmd.candidates.length == 1 ? cmd.candidates.first : null;
      _search.clear();
      _key = const Uuid().v4();
      _error = null;
    });
  }

  int? get _amountPaise => parseRupeesToPaise(_amount.text);

  bool get _canSave =>
      !_saving && _type != null && _amountPaise != null && _picked != null;

  Future<void> _save() async {
    if (!_canSave) return;
    final services = ServicesScope.of(context);
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final type = _type!;
    final paise = _amountPaise!;
    final customer = _picked!;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await services.ledger.addTransaction(
        customerId: customer.id,
        type: type,
        amountPaise: paise,
        method: type == TxnType.jama ? _method : PayMethod.other,
        idempotencyKey: _key,
      );
      navigator.pop();
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            '${type == TxnType.udhaari ? 'Udhaari' : 'Jama'} '
            '${formatPaise(paise)} - ${customer.name} save ho gaya',
          ),
        ),
      );
    } on LedgerException catch (e) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = 'Save nahi ho paya. Dobara koshish karein.';
      });
    }
  }

  List<Customer> _searchResults() {
    final q = normalizeName(_search.text);
    if (q.isEmpty) return _all.take(6).toList();
    return _all
        .where((c) => normalizeName(c.name).contains(q) ||
            (c.phone ?? '').contains(q) ||
            c.code.toLowerCase().contains(q))
        .take(8)
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cmd = _cmd;

    return Scaffold(
      appBar: AppBar(title: const Text('Bolkar likhein')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Jaise bolein: "Ramesh ke 500 rupaye udhaari" ya '
            '"Suresh ne 300 rupaye UPI se jama kiye". '
            'Pehle preview dikhega, aapke confirm karne par hi save hoga.',
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 12),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'hi_IN', label: Text('हिंदी')),
              ButtonSegment(value: 'en_IN', label: Text('English')),
            ],
            selected: {_locale},
            onSelectionChanged:
                _listening ? null : (s) => setState(() => _locale = s.first),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _toggleListening,
            icon: Icon(_listening ? Icons.stop : Icons.mic),
            label: Text(_listening ? 'Ruko aur samjho' : 'Bolna shuru karein'),
          ),
          if (_speechMessage != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(_speechMessage!,
                  style: TextStyle(color: theme.colorScheme.error)),
            ),
          const SizedBox(height: 12),
          TextField(
            controller: _transcript,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Aapne kaha (aap yahan type ya sudhar bhi sakte hain)',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          FilledButton.tonal(
            onPressed: _parse,
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            child: const Text('Samjhein (preview dikhayein)'),
          ),
          if (cmd != null) ...[
            const Divider(height: 32),
            Text('Preview', style: theme.textTheme.titleLarge),
            const SizedBox(height: 8),
            for (final issue in cmd.issues)
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline,
                        size: 18, color: theme.colorScheme.error),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(issue,
                          style: TextStyle(color: theme.colorScheme.error)),
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 8),
            SegmentedButton<TxnType>(
              emptySelectionAllowed: true,
              segments: const [
                ButtonSegment(
                    value: TxnType.udhaari,
                    icon: Icon(Icons.add),
                    label: Text('Udhaari')),
                ButtonSegment(
                    value: TxnType.jama,
                    icon: Icon(Icons.remove),
                    label: Text('Jama')),
              ],
              selected: _type == null ? <TxnType>{} : {_type!},
              onSelectionChanged: (s) =>
                  setState(() => _type = s.isEmpty ? null : s.first),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _amount,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                prefixText: '₹ ',
                labelText: 'Amount',
                border: const OutlineInputBorder(),
                errorText: _amount.text.isNotEmpty && _amountPaise == null
                    ? 'Sahi amount likhein'
                    : null,
              ),
            ),
            if (_type == TxnType.jama) ...[
              const SizedBox(height: 12),
              Text(
                cmd.method == null
                    ? 'Paise kaise mile (tareeka nahi suna, Cash chuna gaya hai - badal sakte hain)'
                    : 'Paise kaise mile',
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  for (final m in PayMethod.values)
                    ChoiceChip(
                      label: Text(methodLabel(m)),
                      selected: _method == m,
                      onSelected: (_) => setState(() => _method = m),
                    ),
                ],
              ),
            ],
            const SizedBox(height: 16),
            Text('Customer', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            if (_picked != null)
              Card(
                child: ListTile(
                  leading: const Icon(Icons.person),
                  title: Text(_picked!.name),
                  subtitle: Text([
                    _picked!.code,
                    if (_picked!.phone != null) _picked!.phone!,
                  ].join(' · ')),
                  trailing: TextButton(
                    onPressed: () => setState(() {
                      _picked = null;
                      _candidates = const [];
                    }),
                    child: const Text('Badlein'),
                  ),
                ),
              )
            else ...[
              if (_candidates.length > 1) ...[
                Text('Kaun sa customer?', style: theme.textTheme.bodyMedium),
                for (final c in _candidates)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.radio_button_unchecked),
                    title: Text(c.name),
                    subtitle: Text([
                      c.code,
                      if (c.phone != null) c.phone!,
                      if (c.address != null) c.address!,
                    ].join(' · ')),
                    onTap: () => setState(() => _picked = c),
                  ),
              ] else ...[
                TextField(
                  controller: _search,
                  onChanged: (_) => setState(() {}),
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search),
                    hintText: 'Customer khojein',
                    border: OutlineInputBorder(),
                  ),
                ),
                for (final c in _searchResults())
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.person_outline),
                    title: Text(c.name),
                    subtitle: Text(c.code),
                    onTap: () => setState(() => _picked = c),
                  ),
              ],
            ],
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(_error!,
                    style: TextStyle(color: theme.colorScheme.error)),
              ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _canSave ? _save : null,
              child: _saving
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Confirm aur Save'),
            ),
          ],
        ],
      ),
    );
  }
}
