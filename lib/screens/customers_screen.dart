import 'package:flutter/material.dart';

import '../core/money.dart';
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

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: Colors.transparent,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute<void>(builder: (_) => const CustomerFormScreen()),
        ),
        icon: const Icon(Icons.person_add),
        label: const Text('Naya customer'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: TextField(
              onChanged: (v) => setState(() => _query = v),
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Naam, phone ya ID se khojein',
                border: OutlineInputBorder(),
              ),
            ),
          ),
          Expanded(
            child: ListenableBuilder(
              listenable: services.bus,
              builder: (context, _) {
                return FutureBuilder<List<CustomerWithBalance>>(
                  future: services.customers.list(query: _query),
                  builder: (context, snap) {
                    if (snap.hasError) {
                      return Center(
                        child: Text('Load nahi hua: ${snap.error}'),
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
                            _query.isEmpty
                                ? 'Abhi koi customer nahi.\n"Naya customer" dabakar shuru karein.'
                                : 'Koi customer nahi mila.',
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
                      itemBuilder: (context, i) => _CustomerTile(item: data[i]),
                    );
                  },
                );
              },
            ),
          ),
        ],
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
      status = 'Baaki';
      color = scheme.error;
    } else if (bal < 0) {
      status = 'Advance';
      color = scheme.primary;
    } else {
      status = 'Barabar';
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
