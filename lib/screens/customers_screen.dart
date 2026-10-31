import 'package:flutter/material.dart';

import '../app.dart' show SettingsScope;
import '../core/money.dart';
import '../l10n/strings.dart';
import '../data/models.dart';
import '../services.dart';
import 'customer_detail_screen.dart';
import 'customer_form_screen.dart';

class CustomersScreen extends StatefulWidget {
  const CustomersScreen({super.key});

  @override
  State<CustomersScreen> createState() => _CustomersScreenState();
}

class _CustomersScreenState extends State<CustomersScreen> {
  String _query = '';
  bool _archived = false;

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: SettingsScope.of(context),
      builder: (context, _) => Scaffold(
        backgroundColor: Colors.transparent,
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const CustomerFormScreen()),
          ),
          icon: const Icon(Icons.person_add),
          label: Text(tr(context, 'cust.new')),
        ),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: TextField(
                onChanged: (v) => setState(() => _query = v),
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.search),
                  hintText: tr(context, 'cust.search'),
                  border: OutlineInputBorder(),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: SegmentedButton<bool>(
                segments: [
                  ButtonSegment(
                    value: false,
                    icon: Icon(Icons.people_outline),
                    label: Text(tr(context, 'cust.active')),
                  ),
                  ButtonSegment(
                    value: true,
                    icon: Icon(Icons.archive_outlined),
                    label: Text(tr(context, 'cust.archived')),
                  ),
                ],
                selected: {_archived},
                onSelectionChanged: (s) => setState(() => _archived = s.first),
              ),
            ),
            Expanded(
              child: ListenableBuilder(
                listenable: services.bus,
                builder: (context, _) {
                  return FutureBuilder<List<CustomerWithBalance>>(
                    future: services.customers
                        .list(query: _query, archived: _archived),
                    builder: (context, snap) {
                      if (snap.hasError) {
                        return Center(
                          child: Text(
                              '${tr(context, 'cust.loadFail')}${snap.error}'),
                        );
                      }
                      final data = snap.data;
                      if (data == null) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (data.isEmpty) {
                        return Center(
                          child: Padding(
                            padding: const EdgeInsets.all(32),
                            child: Text(
                              _query.isNotEmpty
                                  ? tr(context, 'cust.noMatch')
                                  : _archived
                                      ? tr(context, 'cust.emptyArchive')
                                      : tr(context, 'cust.empty'),
                              textAlign: TextAlign.center,
                              style: theme.textTheme.bodyLarge,
                            ),
                          ),
                        );
                      }
                      return ListView.separated(
                        padding: const EdgeInsets.only(bottom: 96),
                        itemCount: data.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, i) =>
                            _CustomerTile(item: data[i]),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CustomerTile extends StatelessWidget {
  const _CustomerTile({required this.item});

  final CustomerWithBalance item;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final c = item.customer;
    final bal = item.balancePaise;
    final String status;
    final Color color;
    if (bal > 0) {
      status = tr(context, 'cust.due');
      color = scheme.error;
    } else if (bal < 0) {
      status = tr(context, 'cust.advance');
      color = scheme.primary;
    } else {
      status = tr(context, 'cust.settled');
      color = scheme.onSurfaceVariant;
    }

    return ListTile(
      leading: CircleAvatar(
        child: Text(c.name.characters.first.toUpperCase()),
      ),
      title: Text(c.name),
      subtitle: Text([c.code, if (c.phone != null) c.phone!].join(' · ')),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(
            formatPaise(bal.abs()),
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w700,
              fontSize: 16,
            ),
          ),
          Text(status, style: TextStyle(color: color, fontSize: 12)),
        ],
      ),
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => CustomerDetailScreen(customerId: c.id),
        ),
      ),
    );
  }
}
