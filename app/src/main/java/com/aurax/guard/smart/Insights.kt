package com.aurax.guard.smart

import com.aurax.guard.AlertType
import com.aurax.guard.EventRecord

data class Incident(
    val count: Int,
    val startMs: Long,
    val endMs: Long,
    val types: List<AlertType>,
    val maxThreat: Int,
    val movedMeters: Int?,
    val photos: Int,
    val lastLink: String?,
    val summary: String,
)

/** Groups close-together alerts into one incident and writes the daily digest. Pure rules, no network. */
object Insights {
    private val QUIET_TYPES = setOf(AlertType.DIGEST)

    fun incident(records: List<EventRecord>, nowMs: Long, windowMin: Int = 30): Incident? {
        val real = records.filter { AlertType.classify(it.reason) !in QUIET_TYPES }.sortedBy { it.timeMs }
        val newest = real.lastOrNull() ?: return null
        if (nowMs - newest.timeMs > 6 * 3_600_000L) return null
        val group = real.filter { newest.timeMs - it.timeMs <= windowMin * 60_000L }
        val serious = group.any { it.risk == 2 || it.threat >= 70 }
        if (group.size < 2 && !serious) return null
        val withPos = group.filter { it.lat != null && it.lon != null }
        val moved = if (withPos.size >= 2) RiskEngine.distanceM(withPos.first().lat!!, withPos.first().lon!!, withPos.last().lat!!, withPos.last().lon!!).toInt() else null
        val types = group.map { AlertType.classify(it.reason) }.distinct()
        val maxT = group.maxOf { it.threat }.coerceAtLeast(0)
        val photos = group.count { it.photoName != null }
        val s = buildString {
            append("${group.size} alerts, ${types.joinToString(", ") { it.title }}. ")
            if (maxT > 0) append("Sabse zyada threat $maxT/100. ")
            if (moved != null) append("Phone ~${TraceRules.fmtDist(moved.toDouble())} chala. ")
            if (photos > 0) append("$photos photo. ")
            append("Pehli: ${EventRecord.formatTime(group.first().timeMs)}, aakhri: ${EventRecord.formatTime(group.last().timeMs)}.")
        }
        return Incident(group.size, group.first().timeMs, group.last().timeMs, types, maxT, moved, photos, group.lastOrNull { it.mapsLink != null }?.mapsLink, s)
    }

    fun digest(records: List<EventRecord>, nowMs: Long, pending: Int, battery: Int): String {
        val day = records.filter { nowMs - it.timeMs in 0..24 * 3_600_000L && AlertType.classify(it.reason) != AlertType.DIGEST }
        val head = "Battery: " + (if (battery >= 0) "$battery%" else "pata nahi") + ". Queue me baaki: $pending."
        if (day.isEmpty()) return "Pichhle 24 ghante me koi alert nahi. Sab shaant. $head"
        val by = day.groupBy { AlertType.classify(it.reason) }
        val parts = by.entries.sortedByDescending { it.value.size }.joinToString(", ") { "${it.key.title}: ${it.value.size}" }
        val top = day.maxByOrNull { it.threat }
        val topTxt = if (top != null && top.threat > 0) " Sabse zyada threat ${top.threat}/100 (${AlertType.classify(top.reason).title})." else ""
        return "Pichhle 24 ghante: ${day.size} alerts ($parts).$topTxt $head"
    }
}
