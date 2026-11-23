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
    val root = ScrollView(act).apply { isFillViewport = true }
    private val body = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(act, 16), Ui.dp(act, 24), Ui.dp(act, 16), Ui.dp(act, 24))
    }

    init {
        root.addView(body)
        refresh()
    }

    fun refresh() {
        val c = act
        body.removeAllViews()
        body.addView(Ui.heading(c, "Activity"))
        body.addView(Ui.dimText(c, "Alerts, events aur scans ki timeline"))

        val alerts = try { EventStore.readAll(c) } catch (_: Exception) { emptyList() }

        body.addView(Ui.button(c, "Report share karein", Ui.Btn.PRIMARY, 14) { shareReport(alerts) })
        body.addView(Ui.button(c, "Poori alert queue (photo, GPS)", Ui.Btn.GHOST) { openQueue() })

        body.addView(Ui.text(c, "Alerts", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 2)) })
        if (alerts.isEmpty()) {
            body.addView(Ui.dimText(c, "Abhi koi alert nahi. Home par 'Test Trigger' se try karein."))
        }
        alerts.take(12).forEach { body.addView(alertCard(it)) }

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
