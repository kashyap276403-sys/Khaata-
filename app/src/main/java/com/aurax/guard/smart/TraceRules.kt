package com.aurax.guard.smart

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One known position of the phone. */
data class Pt(val timeMs: Long, val lat: Double, val lon: Double)

data class TraceCfg(val enabled: Boolean, val threshold: Int, val intervalMin: Int, val criticalPct: Int)
data class TraceState(val active: Boolean = false, val lastTraceMs: Long = 0, val criticalSent: Boolean = false)
enum class TraceAction { START, PERIODIC, CRITICAL, RESET }

/**
 * Emergency Trace rules (pure, unit tested). When the battery falls below the threshold the phone
 * reports its position once at the start, then every interval, plus ONE critical alert per
 * discharge cycle. Everything stops (and resets) once it is charged back above the threshold.
 * While plugged in it pauses: a phone on a charger is normally with its owner.
 */
object TraceRules {
    const val HYSTERESIS = 3
    const val MIN_ALARM_MS = 60_000L

    fun intervalMs(cfg: TraceCfg) = cfg.intervalMin.coerceIn(5, 480) * 60_000L

    fun step(s: TraceState, pct: Int, charging: Boolean, nowMs: Long, cfg: TraceCfg): Pair<TraceState, List<TraceAction>> {
        if (!cfg.enabled) return if (s.active || s.criticalSent) TraceState() to listOf(TraceAction.RESET) else s to emptyList()
        if (pct < 0) return s to emptyList() // battery level unknown
        if (pct >= cfg.threshold + HYSTERESIS) {
            return if (s.active || s.criticalSent) TraceState() to listOf(TraceAction.RESET) else s to emptyList()
        }
        if (charging || pct >= cfg.threshold) return s to emptyList()

        var st = s
        var act: TraceAction? = null
        if (!s.active) {
            st = st.copy(active = true, lastTraceMs = nowMs)
            act = TraceAction.START
        } else if (nowMs - s.lastTraceMs >= intervalMs(cfg)) {
            st = st.copy(lastTraceMs = nowMs)
            act = TraceAction.PERIODIC
        }
        val out = mutableListOf<TraceAction>()
        if (pct <= cfg.criticalPct && !s.criticalSent) {
            st = st.copy(criticalSent = true, lastTraceMs = nowMs)
            out.add(TraceAction.CRITICAL) // the critical alert already carries position and photo
        } else if (act != null) {
            out.add(act)
        }
        return st to out
    }

    /** When the alarm should wake us next for the periodic trace (never sooner than a minute). */
    fun nextTraceAt(s: TraceState, nowMs: Long, cfg: TraceCfg): Long? =
        if (!cfg.enabled || !s.active) null else maxOf(nowMs + MIN_ALARM_MS, s.lastTraceMs + intervalMs(cfg))

    fun fmtDist(m: Double): String = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(Locale.US, m / 1000)

    private fun hhmm(ms: Long) = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    /** "Trail" line for an alert: recent points, how far the phone moved since the last one and in total. */
    fun note(cur: Pt?, earlier: List<Pt>, nowMs: Long, keep: Int = 4): String {
        if (cur == null) return "Trail: abhi location nahi mili."
        val prior = earlier.filter { nowMs - it.timeMs in 0..6 * 3_600_000L && it.timeMs < cur.timeMs }.sortedBy { it.timeMs }.takeLast(keep)
        if (prior.isEmpty()) return "Trail: ye pehla point hai."
        val pts = prior + cur
        var total = 0.0
        for (i in 1 until pts.size) total += RiskEngine.distanceM(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        val last = RiskEngine.distanceM(prior.last().lat, prior.last().lon, cur.lat, cur.lon)
        val move = if (last < 50) "wahin hai" else "pichhle point se ${fmtDist(last)} door"
        return "Trail: ${pts.size} points (${pts.joinToString(" > ") { hhmm(it.timeMs) }}), kul ${fmtDist(total)} chala, $move."
    }
}
