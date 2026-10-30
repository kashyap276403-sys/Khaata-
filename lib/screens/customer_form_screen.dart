import 'package:flutter/material.dart';

import '../data/models.dart';
import '../services.dart';

/// Add a customer, or edit one when [existing] is given.
class CustomerFormScreen extends StatefulWidget {
  const CustomerFormScreen({super.key, this.existing});

  final Customer? existing;

  @override
  State<CustomerFormScreen> createState() => _CustomerFormScreenState();
}

class _CustomerFormScreenState extends State<CustomerFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _phone;
  late final TextEditingController _phone2;
  late final TextEditingController _address;
  late final TextEditingController _notes;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final c = widget.existing;
    _name = TextEditingController(text: c?.name ?? '');
    _phone = TextEditingController(text: c?.phone ?? '');
    _phone2 = TextEditingController(text: c?.phone2 ?? '');
    _address = TextEditingController(text: c?.address ?? '');
    _notes = TextEditingController(text: c?.notes ?? '');
  }

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _phone2.dispose();
    _address.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<bool> _confirmDuplicates(List<Customer> matches) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Milte-julte customer mile'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Kya yeh wahi customer hai? App apne aap kuch merge nahi karta.',
              ),
              const SizedBox(height: 12),
              for (final m in matches)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Text(
                    '${m.name} (${m.code})'
                    '${m.phone != null ? ' · ${m.phone}' : ''}',
                  ),
                ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Wapas jayein'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Phir bhi save karein'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<void> _save() async {
    if (_saving) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final services = ServicesScope.of(context);
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final existing = widget.existing;

    setState(() => _saving = true);
    try {
      final matches = await services.customers.findPossibleMatches(
        name: _name.text,
        phone: _phone.text,
        phone2: _phone2.text,
        excludeId: existing?.id,
      );
      if (matches.isNotEmpty) {
        final go = await _confirmDuplicates(matches);
        if (!go) {
          if (mounted) setState(() => _saving = false);
          return;
        }
      }
      if (existing == null) {
        await services.customers.add(
          name: _name.text,
          phone: _phone.text,
          phone2: _phone2.text,
          address: _address.text,
          notes: _notes.text,
          dueDate: null,
        );
      } else {
        await services.customers.update(
          existing.id,
          name: _name.text,
          phone: _phone.text,
          phone2: _phone2.text,
          address: _address.text,
          notes: _notes.text,
          dueDate: existing.dueDate,
        );
      }
      navigator.pop();
      messenger.showSnackBar(
        SnackBar(
            content: Text(existing == null
                ? 'Customer add ho gaya'
                : 'Customer update ho gaya')),
      );
    } on ArgumentError catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      messenger.showSnackBar(SnackBar(content: Text('${e.message}')));
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      messenger.showSnackBar(SnackBar(content: Text('Save nahi hua: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final editing = widget.existing != null;
    return Scaffold(
      appBar: AppBar(title: Text(editing ? 'Customer edit' : 'Naya customer')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _name,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(
                labelText: 'Naam *',
                border: OutlineInputBorder(),
              ),
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Naam zaroori hai' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Phone',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _phone2,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Doosra phone (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _address,
              decoration: const InputDecoration(
                labelText: 'Pata (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _notes,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Note (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }
}
