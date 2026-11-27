package com.aurax.guard.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aurax.guard.EventStore
import com.aurax.guard.Gate
import com.aurax.guard.GuardService
import com.aurax.guard.Prefs
import com.aurax.guard.smart.BatterySaver
import com.aurax.guard.smart.Lockdown
import com.aurax.guard.smart.SmartPrefs
import com.aurax.guard.smart.Tag
import com.aurax.guard.smart.TelemetryRules
import com.aurax.guard.smart.Timeline

/**
 * Live stream: real app events in monospace lines (guard states, Telegram commands, tamper/SIM/USB, copy progress).
 * It refreshes only while this screen is visible (every 3 s, and only when something changed): no animation loop,
 * no background work, nothing runs when you leave the screen.
 */
class TelemetryActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private lateinit var pauseBtn: android.widget.Button
    private var lastHash = 0
    private var paused = false
    private val tick = object : Runnable {
        override fun run() {
            if (!paused) refresh()
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val c = this
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 12)) }
        col.addView(Ui.heading(c, "Live stream"))
        col.addView(Ui.dimText(c, "Asli events, naye neeche. Sirf is screen ke khule rehne tak update hota hai."))
        pauseBtn = Ui.button(c, "Rokein (pause)", Ui.Btn.GHOST, 6) {
            paused = !paused
            pauseBtn.text = if (paused) "Chalu karein (resume)" else "Rokein (pause)"
            if (!paused) refresh()
        }
        col.addView(pauseBtn)
        out = TextView(c).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11.5f
            setTextColor(Color.rgb(190, 255, 225))
            setTextIsSelectable(true)
            setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10))
            background = Ui.glass(c, 6, Ui.emerald, strong = true)
        }
        scroll = ScrollView(c).apply { addView(out) }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = Ui.dp(c, 8) })
        setContentView(LinearLayout(c).apply { background = Ui.backdrop(c); addView(col, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT) })
    }

    override fun onResume() { super.onResume(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun color(t: Tag) = when (t) {
        Tag.CMD -> Color.rgb(0, 229, 255)
        Tag.COPY -> Color.rgb(160, 200, 255)
        Tag.TAMPER -> Color.rgb(255, 107, 107)
        Tag.GUARD -> Color.rgb(0, 255, 153)
        Tag.NET -> Color.rgb(255, 200, 87)
        Tag.SYS -> Color.rgb(190, 215, 205)
    }

    private fun refresh() {
        try {
            val c = this
            val head = buildString {
                appendLine("$ auraX-guard --state")
                appendLine("guard=${if (!Prefs.guardOn(c)) "OFF" else if (GuardService.instance != null) "ON" else "STOPPED"} park=${onOff(Prefs.parkOn(c))} stealth=${onOff(SmartPrefs.stealth(c))}")
                appendLine("saver=${onOff(BatterySaver.active(c))} trace=${onOff(SmartPrefs.traceOn(c))} lockdown=${onOff(Lockdown.active(c))} remote=${onOff(com.aurax.guard.remote.RemotePrefs.masterOn(c))}")
                appendLine("queue=${try { EventStore.pendingCount(c) } catch (_: Exception) { 0 }}")
                appendLine("-- alerts --")
                val recent = try { EventStore.readAll(c).sortedBy { it.timeMs }.takeLast(8) } catch (_: Exception) { emptyList() }
                if (recent.isEmpty()) appendLine("(koi alert nahi)") else recent.forEach { appendLine(Timeline.line(it, false)) }
                appendLine("-- log --")
            }
            val lines = TelemetryRules.lines(Prefs.events(c), 70)
            val text = head + lines.joinToString("\n") { "[${it.first.label}] ${it.second}" }
            if (!TelemetryRules.changed(lastHash, text)) return
            lastHash = text.hashCode()
            val sb = SpannableStringBuilder(head)
            sb.setSpan(ForegroundColorSpan(Color.rgb(0, 255, 153)), 0, head.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            lines.forEach { (t, m) ->
                val s = "[${t.label}] $m\n"
                val st = sb.length
                sb.append(s)
                sb.setSpan(ForegroundColorSpan(color(t)), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val atBottom = scroll.scrollY + scroll.height >= out.height - 80
            out.text = sb
            if (atBottom) scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        } catch (_: Exception) {
        }
    }

    private fun onOff(b: Boolean) = if (b) "ON" else "OFF"
}
