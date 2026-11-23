package com.aurax.guard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Local alert queue: every trigger with photo name, GPS link, battery, network, time. */
class EventsActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var header: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) {
            finish()
            return
        }
        if (Prefs.secureScreen(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(40))
        }
        header = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(header)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) {
            finish()
            return
        }
        render()
    }

    private fun render() {
        list.removeAllViews()
        val all = EventStore.readAll(this)
        header.text = "Alert queue (${all.size}, ${all.count { !it.sent }} pending)"
        if (all.isEmpty()) {
            list.addView(text("Abhi koi alert nahi. Main screen par 'Test Trigger' dabakar try karein.", 14f, dim = true))
            return
        }
        for (r in all.take(50)) list.addView(card(r))
    }

    private fun card(r: EventRecord): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = com.aurax.guard.ui.Ui.glass(this@EventsActivity, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) }
        }
        c.addView(text(r.reason, 17f, bold = true).apply { setTextColor(Color.rgb(80, 220, 160)) })
        run {
            val ctx: android.content.Context = this@EventsActivity
            val cfg = com.aurax.guard.telegram.TelegramConfig
            val badge = com.aurax.guard.telegram.Badges.forRecord(r, cfg.configured(ctx), cfg.sinceMs(ctx), com.aurax.guard.telegram.TelegramDispatcher.isOnline(ctx))
            val rowv = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val lbl = TextView(ctx)
            lbl.text = "Device: " + cfg.label(ctx)
            lbl.textSize = 13f
            lbl.maxLines = 1
            lbl.ellipsize = android.text.TextUtils.TruncateAt.END
            lbl.setTextColor(android.graphics.Color.WHITE)
            lbl.setTypeface(lbl.typeface, android.graphics.Typeface.BOLD)
            rowv.addView(lbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            rowv.addView(com.aurax.guard.ui.Ui.statusBadge(ctx, badge))
            c.addView(rowv, 0)
        }

        c.addView(text(EventRecord.formatTime(r.timeMs), 13f, dim = true))
        c.addView(
            text(
                "Battery: " + (if (r.batteryPct >= 0) "${r.batteryPct}%" else "?") +
                    (if (r.charging) " (charging)" else "") + "   Network: " + r.network.ifBlank { "?" },
                14f,
            )
        )
        val link = r.mapsLink
        c.addView(
            text(
                if (link != null) "Location: ${r.locationNote}" + (r.accuracyM?.let { " (+-${it.toInt()} m)" } ?: "")
                else "Location nahi mili: ${r.locationNote}",
                14f,
            )
        )
        com.aurax.guard.ui.Ui.riskBadge(this, r.risk)?.let { rb ->
            c.addView(rb.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) } })
        }
        if (r.smart.isNotBlank()) c.addView(text("Smart: ${r.smart}", 13f, dim = true))
        if (r.photoName != null) c.addView(text("Photo: ${r.photoName}", 13f, dim = true))
        c.addView(text(if (r.sent) "Status: bhej di gayi" else "Status: queue me (abhi bhejna baaki)", 12f, dim = true))
        if (link != null) {
            c.addView(button("Google Maps me kholein") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Maps nahi khula", Toast.LENGTH_SHORT).show()
                }
            })
        }
        c.addView(button("Report copy karein") {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("auraX Guard report", r.report()))
            Toast.makeText(this, "Report copy ho gayi", Toast.LENGTH_SHORT).show()
        })
        c.addView(button("Ye entry mitayein", danger = true) { confirmDelete(r) })
        return c
    }

    private fun confirmDelete(r: EventRecord) {
        AlertDialog.Builder(this)
            .setMessage("Ye alert queue se hamesha ke liye mit jayegi. Mitayein?")
            .setPositiveButton("Mitayein") { _, _ ->
                val keep = EventStore.readAll(this).reversed().filter { it.id != r.id }
                EventStore.clear(this)
                keep.forEach { EventStore.append(this, it) }
                render()
            }
            .setNegativeButton("Rehne dein", null)
            .create().also { d -> d.setOnShowListener { com.aurax.guard.ui.Ui.styleDialog(d) } }.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(if (dim) Color.argb(185, 255, 255, 255) else Color.WHITE)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun button(label: String, danger: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (danger) Color.argb(110, 255, 110, 110) else Color.argb(70, 80, 220, 160))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) }
    }
}
