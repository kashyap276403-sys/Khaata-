package com.aurax.guard.smart

import com.aurax.guard.AlertType
import com.aurax.guard.EventRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cyber Incident Timeline: one clean line per event, and a plain-text export. Pure and unit tested. */
object Timeline {
    private fun t(ms: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(ms))

    fun line(r: EventRecord, withLocation: Boolean): String {
        val bat = if (r.batteryPct >= 0) "${r.batteryPct}%" + (if (r.charging) "+" else "") else "--"
        val risk = if (r.threat >= 0) "T${r.threat}" else "T--"
        val guard = r.guard.ifBlank { "guard ?" }
        val type = AlertType.classify(r.reason).title
        val loc = if (withLocation && r.mapsLink != null) " | ${r.mapsLink}" else ""
        return "${t(r.timeMs)} | $bat | ${r.network.ifBlank { "net ?" }} | $guard | $risk | $type: ${r.reason.take(80)}$loc"
    }

    fun export(list: List<EventRecord>, label: String, nowMs: Long, withLocation: Boolean, max: Int = 200): String {
        val rows = list.sortedBy { it.timeMs }.takeLast(max)
        return buildString {
            appendLine("Cyber Incident Timeline - $label")
            appendLine("Banaya gaya: ${t(nowMs)} | ${rows.size} events | location: ${if (withLocation) "shamil" else "nahi"}")
            appendLine("Samay | battery(+charge) | network | guard | threat | event")
            appendLine("-".repeat(40))
            if (rows.isEmpty()) appendLine("Koi event nahi.")
            rows.forEach { appendLine(line(it, withLocation)) }
        }.trimEnd()
    }

    fun guardTag(guardOn: Boolean, running: Boolean, saver: Boolean, lockdown: Boolean): String =
        (if (!guardOn) "guard OFF" else if (running) "guard ON" else "guard ruka") +
            (if (lockdown) "+lockdown" else "") + (if (saver) "+saver" else "")
}
