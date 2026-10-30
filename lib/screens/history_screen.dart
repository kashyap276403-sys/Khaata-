import 'package:flutter/material.dart';

import '../core/text_utils.dart';
import '../data/activity.dart';
import '../data/models.dart';
import '../services.dart';
import '../widgets/txn_tile.dart';

enum _Range { today, week, month, all }

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('History'),
          bottom: const TabBar(
            tabs: [Tab(text: 'Entries'), Tab(text: 'Activity')],
          ),
        ),
        body: const TabBarView(children: [_EntriesTab(), _ActivityTab()]),
      ),
    );
  }
}

class _EntriesTab extends StatefulWidget {
  const _EntriesTab();

  @override
  State<_EntriesTab> createState() => _EntriesTabState();
}

class _EntriesTabState extends State<_EntriesTab> {
  _Range _range = _Range.all;
  String _query = '';
  bool _newestFirst = true;

  (int?, int?) _window() {
    final n = DateTime.now();
    final startToday = DateTime(n.year, n.month, n.day);
    final tomorrow = DateTime(n.year, n.month, n.day + 1);
    switch (_range) {
      case _Range.today:
        return (
          startToday.millisecondsSinceEpoch,
          tomorrow.millisecondsSinceEpoch
        );
      case _Range.week:
        return (
          DateTime(n.year, n.month, n.day - 6).millisecondsSinceEpoch,
          tomorrow.millisecondsSinceEpoch,
        );
      case _Range.month:
        return (
          DateTime(n.year, n.month, n.day - 29).millisecondsSinceEpoch,
          tomorrow.millisecondsSinceEpoch,
        );
      case _Range.all:
        return (null, null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    final w = _window();
    const labels = {
      _Range.today: 'Aaj',
      _Range.week: '7 din',
      _Range.month: '30 din',
      _Range.all: 'Sab',
    };

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: TextField(
            onChanged: (v) => setState(() => _query = v),
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              hintText: 'Customer ke naam se khojein',
              border: OutlineInputBorder(),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              Expanded(
                child: Wrap(
                  spacing: 8,
                  children: [
                    for (final r in _Range.values)
                      ChoiceChip(
                        label: Text(labels[r]!),
                        selected: _range == r,
                        onSelected: (_) => setState(() => _range = r),
                      ),
                  ],
                ),
              ),
              IconButton(
                tooltip: _newestFirst ? 'Pehle purani' : 'Pehle nayi',
                icon: Icon(
                  _newestFirst ? Icons.arrow_downward : Icons.arrow_upward,
                ),
                onPressed: () => setState(() => _newestFirst = !_newestFirst),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListenableBuilder(
            listenable: services.bus,
            builder: (context, _) => FutureBuilder<List<RecentTxn>>(
              future: services.ledger.history(
                fromMs: w.$1,
                toMs: w.$2,
                query: _query,
                newestFirst: _newestFirst,
              ),
              builder: (context, snap) {
                if (snap.hasError) {
                  return Center(child: Text('Load nahi hua: ${snap.error}'));
                }
                final data = snap.data;
                if (data == null) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (data.isEmpty) {
                  return const Center(
                      child: Text('Is filter me koi entry nahi.'));
                }
                return ListView.builder(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: data.length,
                  itemBuilder: (context, i) => TxnTile(
                    txn: data[i].txn,
                    customerName: data[i].customerName,
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }
}

class _ActivityTab extends StatelessWidget {
  const _ActivityTab();

  @override
  Widget build(BuildContext context) {
    final services = ServicesScope.of(context);
    return ListenableBuilder(
      listenable: services.bus,
      builder: (context, _) => FutureBuilder<List<ActivityItem>>(
        future: services.ledger.activity(),
        builder: (context, snap) {
          if (snap.hasError) {
            return Center(child: Text('Load nahi hua: ${snap.error}'));
          }
          final data = snap.data;
          if (data == null) {
            return const Center(child: CircularProgressIndicator());
          }
          if (data.isEmpty) {
            return const Center(child: Text('Abhi koi activity nahi.'));
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: data.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) => ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(describeActivity(data[i])),
              subtitle: Text(formatDateTime(data[i].at)),
            ),
          );
        },
      ),
    );
  }
}
