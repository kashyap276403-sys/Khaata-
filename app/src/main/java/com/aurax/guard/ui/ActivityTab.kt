package com.aurax.guard.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.aurax.guard.EventRecord
import com.aurax.guard.EventStore
import com.aurax.guard.Prefs
import com.aurax.guard.ReportBuilder
import com.aurax.guard.permissions.AuditStore
import com.aurax.guard.permissions.RiskLevel

/** Activity: timeline of alerts, system events and scan history, plus a shareable report. */
class ActivityTab(private val act: Activity, private val openQueue: () -> Unit) {
    private var typeFilter: String? = null
    private var deviceFilter: String? = null
    private var query = ""
    private var riskFilter: Int? = null

    val root = ScrollView(act).apply { isFillViewport = true }
    private val body = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(act, 16), Ui.dp(act, 24), Ui.dp(act, 16), Ui.dp(act, 24))
    }

    init {
        root.addView(body)
        refresh()
    }

    fun refresh() = Ui.keepScroll(body) { refreshNow() }

    private fun refreshNow() {
        val c = act
        body.removeAllViews()
        body.addView(Ui.heading(c, "Activity"))
        body.addView(Ui.dimText(c, "Alerts, events aur scans ki timeline"))

        val alerts = try { EventStore.readAll(c) } catch (_: Exception) { emptyList() }

        body.addView(Ui.button(c, "Report share karein", Ui.Btn.PRIMARY, 14) { shareReport(alerts) })
        body.addView(Ui.button(c, "Poori alert queue (photo, GPS)", Ui.Btn.GHOST) { openQueue() })

        body.addView(Ui.text(c, "Alerts", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 2)) })

        // ---- search + filters ----
        val search = android.widget.EditText(c).apply {
            hint = "Dhoondhein (naam, location, network...)"
            setText(query)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.argb(150, 255, 255, 255))
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { v, _, _ -> query = v.text.toString().trim(); refresh(); true }
        }
        body.addView(search)
        val typeChips = mutableListOf(Ui.chip(c, "Sab", typeFilter == null) { typeFilter = null; refresh() })
        com.aurax.guard.AlertType.values().forEach { t ->
            if (alerts.any { com.aurax.guard.AlertType.classify(it.reason) == t }) typeChips.add(Ui.chip(c, t.title, typeFilter == t.key) { typeFilter = t.key; refresh() })
        }
        body.addView(Ui.chipRow(c, typeChips, 6))
        body.addView(Ui.chipRow(c, listOf(
            Ui.chip(c, "Koi bhi risk", riskFilter == null) { riskFilter = null; refresh() },
            Ui.chip(c, "High", riskFilter == 2) { riskFilter = 2; refresh() },
            Ui.chip(c, "Medium", riskFilter == 1) { riskFilter = 1; refresh() },
            Ui.chip(c, "Low", riskFilter == 0) { riskFilter = 0; refresh() },
        ), 4))
        val devices = (alerts.map { it.device } + listOf(com.aurax.guard.telegram.TelegramConfig.label(c))).filter { it.isNotBlank() }.distinct()
        if (devices.size > 1 || deviceFilter != null) {
            val dc = mutableListOf(Ui.chip(c, "Sab devices", deviceFilter == null) { deviceFilter = null; refresh() })
            devices.forEach { d -> dc.add(Ui.chip(c, d, deviceFilter == d) { deviceFilter = d; refresh() }) }
            body.addView(Ui.chipRow(c, dc, 4))
        }
        val shown = filtered(alerts)
        if (alerts.isEmpty()) {
            body.addView(Ui.dimText(c, "Abhi koi alert nahi. Home par 'Test Trigger' se try karein."))
        } else if (shown.isEmpty()) {
            body.addView(Ui.dimText(c, "Is filter/search se kuch nahi mila."))
        } else {
            body.addView(Ui.dimText(c, "${shown.size} / ${alerts.size} alerts", 12f))
        }
        shown.take(30).forEach { body.addView(alertCard(it)) }

        // ---- copy / clear ----
        val tools = Ui.card(c, null, 14)
        tools.addView(Ui.text(c, "Log tools", 15f, bold = true))
        tools.addView(Ui.button(c, "Summary copy karein", Ui.Btn.GHOST, 8) { copySummary(shown.ifEmpty { alerts }) })
        tools.addView(Ui.button(c, "Cyber Timeline export / share", Ui.Btn.GHOST, 6) { exportTimeline(shown.ifEmpty { alerts }) })
        tools.addView(Ui.button(c, "Bheji ja chuki / sirf-local alerts saaf karein", Ui.Btn.GHOST, 6) { confirmClearAlerts(alerts) })
        tools.addView(Ui.button(c, "System events log saaf karein", Ui.Btn.DANGER, 6) { confirmClearLog() })
        tools.addView(Ui.dimText(c, "Queue me jo alert abhi Telegram par bhejna baaki hai wo kabhi nahi hatayi jati."))
        body.addView(tools)

        body.addView(Ui.text(c, "Scan history", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 2)) })
        val sc = Ui.card(c, null, 8)
        val apps = AuditStore.apps
        if (apps == null) {
            sc.addView(Ui.dimText(c, "App permission scan abhi nahi hua."))
        } else {
            val user = apps.filter { !it.facts.isSystem }
            sc.addView(Ui.text(c, "Aakhri app scan: " + EventRecord.formatTime(AuditStore.scannedAtMs), 14f, bold = true))
            sc.addView(Ui.dimText(c, "${user.size} apps, High: ${AuditStore.count(RiskLevel.HIGH)}, Medium: ${AuditStore.count(RiskLevel.MEDIUM)}"))
        }
        sc.addView(Ui.dimText(c, "Safai history: abhi koi safai nahi hui (cleaner agle step me).", 12f))
        body.addView(sc)

        body.addView(Ui.text(c, "System events", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 2)) })
        val ev = Ui.card(c, null, 8)
        val lines = Prefs.events(c).take(15)
        if (lines.isEmpty()) ev.addView(Ui.dimText(c, "Abhi kuch nahi hua."))
        lines.forEach { ev.addView(Ui.dimText(c, it, 12f)) }
        body.addView(ev)
    }

    private fun filtered(all: List<EventRecord>): List<EventRecord> {
        val q = query.lowercase()
        return all.filter { r ->
            (typeFilter == null || com.aurax.guard.AlertType.classify(r.reason).key == typeFilter) &&
                (deviceFilter == null || r.device == deviceFilter) &&
                (riskFilter == null || r.risk == riskFilter) &&
                (q.isEmpty() || (r.reason + " " + r.locationNote + " " + r.network + " " + r.device).lowercase().contains(q))
        }
    }

    /** User-started only: nothing is shared unless you pick an app in the share sheet. */
    private fun exportTimeline(list: List<EventRecord>) {
        fun share(withLoc: Boolean) {
            try {
                val txt = com.aurax.guard.smart.Timeline.export(list, com.aurax.guard.telegram.TelegramConfig.label(act), System.currentTimeMillis(), withLoc)
                act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Cyber Incident Timeline").putExtra(Intent.EXTRA_TEXT, txt), "Timeline share karein"))
            } catch (_: Exception) {
                Toast.makeText(act, "Share nahi hua", Toast.LENGTH_SHORT).show()
            }
        }
        Ui.showGlass(android.app.AlertDialog.Builder(act).setTitle("Timeline export")
            .setMessage("${list.size.coerceAtMost(200)} events ka text banega (samay, battery, network, guard state, threat). Location Maps link ke roop me ja sakti hai: sirf bharosemand jagah bhejein.")
            .setPositiveButton("Bina location") { _, _ -> share(false) }
            .setNeutralButton("Location ke saath") { _, _ -> share(true) }
            .setNegativeButton("Rehne dein", null))
    }

    private fun copySummary(list: List<EventRecord>) {
        val apps = AuditStore.apps?.filter { !it.facts.isSystem }
        val text = ReportBuilder.build(System.currentTimeMillis(), list, AuditStore.count(RiskLevel.HIGH), AuditStore.count(RiskLevel.MEDIUM), apps?.size)
        try {
            act.getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("auraX Guard summary", text))
            Toast.makeText(act, "Summary copy ho gayi (isme location ho sakti hai)", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(act, "Copy nahi hui", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAlerts(all: List<EventRecord>) {
        val cfg = com.aurax.guard.telegram.TelegramConfig
        val since = cfg.sinceMs(act)
        val removable = all.count { it.sent || !cfg.configured(act) || it.timeMs < since }
        if (removable == 0) { Toast.makeText(act, "Saaf karne layak kuch nahi (baaki alerts bhejna baaki hain)", Toast.LENGTH_LONG).show(); return }
        Ui.showGlass(android.app.AlertDialog.Builder(act)
            .setTitle("$removable alerts saaf karein?")
            .setMessage("Sirf wo alerts hatenge jo bheji ja chuki hain ya sirf phone me hain. Jo Telegram par bhejna baaki hain wo rahenge. Ye wapas nahi aayenge.")
            .setPositiveButton("Haan, saaf karo") { _, _ ->
                val n = EventStore.removeWhere(act) { it.sent || !cfg.configured(act) || it.timeMs < since }
                Prefs.log(act, "Aapke kehne par $n purani alerts saaf ki")
                refresh()
            }.setNegativeButton("Nahi", null))
    }

    private fun confirmClearLog() {
        Ui.showGlass(android.app.AlertDialog.Builder(act)
            .setTitle("System events log saaf karein?")
            .setMessage("Sirf ye text log (neeche 'System events') saaf hoga. Alerts aur photos nahi hatenge.")
            .setPositiveButton("Haan") { _, _ -> Prefs.clearEvents(act); refresh() }
            .setNegativeButton("Nahi", null))
    }

    private fun alertCard(r: EventRecord): LinearLayout {
        val c = act
        val card = Ui.card(c, null, 10)
        card.addView(Ui.text(c, r.reason, 16f, bold = true, color = Ui.emerald))
        run {
            val ctx: android.content.Context = c
            val cfg = com.aurax.guard.telegram.TelegramConfig
            val badge = com.aurax.guard.telegram.Badges.forRecord(r, cfg.configured(ctx), cfg.sinceMs(ctx), com.aurax.guard.telegram.TelegramDispatcher.isOnline(ctx))
            val rowv = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val lbl = android.widget.TextView(ctx)
            lbl.text = "Device: " + cfg.label(ctx)
            lbl.textSize = 13f
            lbl.maxLines = 1
            lbl.ellipsize = android.text.TextUtils.TruncateAt.END
            lbl.setTextColor(android.graphics.Color.WHITE)
            lbl.setTypeface(lbl.typeface, android.graphics.Typeface.BOLD)
            rowv.addView(lbl, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            rowv.addView(com.aurax.guard.ui.Ui.statusBadge(ctx, badge))
            card.addView(rowv, 0)
        }

        card.addView(Ui.dimText(c, EventRecord.formatTime(r.timeMs), 12f))
        card.addView(Ui.dimText(c,
            "Battery " + (if (r.batteryPct >= 0) "${r.batteryPct}%" else "?") + "  |  " + r.network.ifBlank { "network ?" } +
                "  |  " + (if (r.mapsLink != null) "GPS mili" else "GPS nahi") +
                (if (r.photoName != null) "  |  photo" else ""), 13f))
        run {
            val rb = Ui.riskBadge(c, r.risk, r.threat)
            if (rb != null) card.addView(rb.apply { layoutParams = Ui.lp(c, 6).apply { width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT } })
            if (r.smart.isNotBlank()) card.addView(Ui.dimText(c, r.smart, 12f))
        }
        val link = r.mapsLink
        if (link != null) {
            card.addView(Ui.button(c, "Maps me kholein", Ui.Btn.GHOST) {
                try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                catch (_: Exception) { Toast.makeText(c, "Maps nahi khula", Toast.LENGTH_SHORT).show() }
            })
        }
        return card
    }

    private fun shareReport(alerts: List<EventRecord>) {
        val apps = AuditStore.apps?.filter { !it.facts.isSystem }
        val text = ReportBuilder.build(
            System.currentTimeMillis(), alerts,
            AuditStore.count(RiskLevel.HIGH), AuditStore.count(RiskLevel.MEDIUM), apps?.size,
        )
        try {
            act.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    "Report bhejein (isme location ho sakti hai)",
                )
            )
        } catch (_: Exception) {
            Toast.makeText(act, "Share nahi hua", Toast.LENGTH_SHORT).show()
        }
    }
}
