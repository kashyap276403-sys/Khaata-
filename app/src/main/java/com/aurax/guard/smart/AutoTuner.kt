package com.aurax.guard.smart

import android.content.Context
import com.aurax.guard.AlertType
import com.aurax.guard.EventStore
import com.aurax.guard.GuardService
import com.aurax.guard.Prefs

data class Tune(val newLevel: Int, val newGrace: Int, val reason: String)

/**
 * Suggests (and, only if you switch it on, applies) a gentler motion setting when alerts look accidental.
 * Guardrails: only ever makes the guard LESS touchy by one step, at most once a day, never turns protection off,
 * never acts when anything else (wrong password, SIM change, phone moving far) backs the alerts up, and can be undone.
 */
object AutoTuner {
    private const val DAY = 24L * 3600 * 1000
    const val MAX_LEVEL = 2
    const val MAX_GRACE = 30

    fun suggest(hist: List<Hist>, nowMs: Long, level: Int, grace: Int, lastTuneMs: Long): Tune? {
        if (nowMs - lastTuneMs < DAY) return null
        val day = hist.filter { nowMs - it.timeMs in 0..DAY }
        val motion = day.filter { it.type == AlertType.MOTION }
        if (motion.size < 4) return null
        if (day.any { it.type == AlertType.WRONG_UNLOCK || it.type == AlertType.SIM }) return null
        val pos = motion.filter { it.lat != null && it.lon != null }
        for (a in pos) for (b in pos) {
            if (RiskEngine.distanceM(a.lat!!, a.lon!!, b.lat!!, b.lon!!) > 300) return null // phone really travelled
        }
        val why = "${motion.size} jhatka alerts 24 ghante me, koi aur sanket (galat password/SIM/door jana) nahi: shayad galat trigger."
        return when {
            level < MAX_LEVEL -> Tune(level + 1, grace, why)
            grace < MAX_GRACE -> Tune(level, (grace + 10).coerceAtMost(MAX_GRACE), why)
            else -> null
        }
    }

    fun current(ctx: Context): Tune? = try {
        suggest(RiskEngine.historyOf(EventStore.readAll(ctx)), System.currentTimeMillis(),
            Prefs.sensitivity(ctx), Prefs.graceSec(ctx), Prefs.autoTuneLastMs(ctx))
    } catch (_: Exception) {
        null
    }

    /** Applies one suggestion and remembers the old values so it can be undone. */
    fun apply(ctx: Context, t: Tune, auto: Boolean) {
        Prefs.setAutoPrev(ctx, Prefs.sensitivity(ctx), Prefs.graceSec(ctx))
        Prefs.setSensitivity(ctx, t.newLevel)
        Prefs.setGraceSec(ctx, t.newGrace)
        Prefs.setAutoTuneLastMs(ctx, System.currentTimeMillis())
        val note = "Level: ${Prefs.snatchNames[t.newLevel]}, grace ${t.newGrace}s"
        Prefs.setAutoNote(ctx, note)
        Prefs.log(ctx, (if (auto) "Smart auto-adjust: " else "Smart suggestion lagayi: ") + note)
        try { GuardService.instance?.reloadMotion() } catch (_: Exception) {}
    }

    fun undo(ctx: Context): Boolean {
        val (l, g) = Prefs.autoPrev(ctx) ?: return false
        Prefs.setSensitivity(ctx, l)
        Prefs.setGraceSec(ctx, g)
        Prefs.clearAutoPrev(ctx)
        Prefs.setAutoNote(ctx, "")
        Prefs.log(ctx, "Smart badlav wapas liya: ${Prefs.snatchNames[l]}, grace ${g}s")
        try { GuardService.instance?.reloadMotion() } catch (_: Exception) {}
        return true
    }

    /** Called after each alert is saved. Does nothing unless auto-adjust is switched on. */
    fun maybeAuto(ctx: Context) {
        if (!Prefs.autoTune(ctx)) return
        current(ctx)?.let { apply(ctx, it, auto = true) }
    }
}
