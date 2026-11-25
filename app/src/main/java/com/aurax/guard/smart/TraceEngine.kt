package com.aurax.guard.smart

import android.content.Context
import com.aurax.guard.AlertType
import com.aurax.guard.EventRecord
import com.aurax.guard.EventRecorder
import com.aurax.guard.GuardService
import com.aurax.guard.EventRecorder.PhotoMode
import com.aurax.guard.Prefs
import com.aurax.guard.telegram.TelegramConfig

/** Android side of Emergency Trace and Guard Boost. Everything goes through the normal alert queue. */
object TraceEngine {
    /** Battery level changed (or periodic alarm): run the pure state machine and send what it asks for. */
    @Synchronized
    fun onBattery(ctx: Context, pct: Int, charging: Boolean) {
        val app = ctx.applicationContext
        val cfg = SmartPrefs.cfg(app)
        val (next, acts) = TraceRules.step(SmartPrefs.state(app), pct, charging, System.currentTimeMillis(), cfg)
        SmartPrefs.setState(app, next)
        for (a in acts) {
            when (a) {
                TraceAction.RESET -> Prefs.log(app, "Emergency trace reset (battery theek)")
                TraceAction.START -> send(app, "Emergency trace: battery $pct%", cfg.enabled && SmartPrefs.tracePhoto(app))
                TraceAction.PERIODIC -> send(app, "Emergency trace: battery $pct%", SmartPrefs.tracePhoto(app))
                TraceAction.CRITICAL -> send(app, "Emergency trace: Battery CRITICAL ($pct%) aakhri location", SmartPrefs.tracePhoto(app))
            }
        }
        SmartScheduler.arm(app)
    }

    private fun send(app: Context, reason: String, wantPhoto: Boolean) {
        val canPhoto = wantPhoto && GuardService.cameraCapable
        val note = if (wantPhoto && !canPhoto) "Photo: nahi li (camera abhi available nahi), location/status bheji." else null
        EventRecorder.trigger(app, reason, TelegramConfig.label(app), if (canPhoto) PhotoMode.CAPTURE_NOW else PhotoMode.NONE, note) {}
    }

    // ---- Guard Boost: extra snapshots after a HIGH risk event ----
    private const val BOOST_MS = 30 * 60_000L
    private const val FIRST_MS = 45_000L
    private const val EVERY_MS = 5 * 60_000L
    private const val MAX = 6
    private fun maxNow(app: Context) = if (BatterySaver.active(app)) BatterySaver.BOOST_MAX_SAVER else MAX

    fun afterEvent(app: Context, rec: EventRecord) {
        if (!SmartPrefs.boostOn(app)) return
        val t = AlertType.classify(rec.reason)
        if (t == AlertType.TRACE || t == AlertType.DIGEST || rec.risk != 2) return
        val now = System.currentTimeMillis()
        SmartPrefs.setBoost(app, now + BOOST_MS, now, 0)
        Prefs.log(app, "Guard Boost chalu (30 min)")
        SmartScheduler.arm(app)
    }

    fun boostDue(app: Context, now: Long): Boolean {
        if (!SmartPrefs.boostOn(app)) return false
        val until = SmartPrefs.boostUntil(app)
        if (now >= until || SmartPrefs.boostCount(app) >= maxNow(app)) return false
        val n = SmartPrefs.boostCount(app)
        return now - SmartPrefs.boostLast(app) >= if (n == 0) FIRST_MS else EVERY_MS
    }

    fun boostNextAt(app: Context, now: Long): Long? {
        if (!SmartPrefs.boostOn(app) || now >= SmartPrefs.boostUntil(app) || SmartPrefs.boostCount(app) >= maxNow(app)) return null
        val n = SmartPrefs.boostCount(app)
        return maxOf(now + 15_000L, SmartPrefs.boostLast(app) + if (n == 0) FIRST_MS else EVERY_MS)
    }

    fun runBoost(app: Context) {
        val now = System.currentTimeMillis()
        val n = SmartPrefs.boostCount(app)
        SmartPrefs.setBoost(app, SmartPrefs.boostUntil(app), now, n + 1)
        send(app, "Guard Boost: snapshot ${n + 1}/$MAX", true)
    }
}
