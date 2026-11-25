package com.aurax.guard.smart

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurax.guard.AlertType
import com.aurax.guard.DeviceInfo
import com.aurax.guard.EventRecorder
import com.aurax.guard.EventStore
import com.aurax.guard.EventRecorder.PhotoMode
import com.aurax.guard.Prefs
import com.aurax.guard.telegram.TelegramConfig
import java.util.Calendar

/** One inexact, doze-friendly alarm that wakes for trace / boost / digest / heartbeat, whichever is next. */
object SmartScheduler {
    private fun pi(c: Context) = PendingIntent.getBroadcast(
        c, 7311, Intent(c, SmartAlarmReceiver::class.java).setAction("com.aurax.guard.SMART_TICK"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun nextDigestAt(c: Context, now: Long): Long? {
        if (!SmartPrefs.digestOn(c)) return null
        val m = SmartPrefs.digestMin(c)
        val cal = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Wake the scheduler at a specific time (used by the watchdog after the app is swiped away). */
    fun armAt(c: Context, atMs: Long) {
        try {
            val am = c.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(c.applicationContext))
        } catch (_: Exception) {}
    }

    /** Watchdog: the switch says ON but the service died (battery manager, crash): restart it. Never turns anything OFF. */
    fun ensureGuard(app: Context) {
        try {
            if (GuardRules.shouldRestart(Prefs.guardOn(app), com.aurax.guard.GuardService.instance != null)) {
                com.aurax.guard.GuardService.start(app, false)
                Prefs.log(app, "Watchdog: guard ruka tha, dobara chalu kiya")
            }
        } catch (e: Exception) {
            Prefs.log(app, "Watchdog: guard dobara chalu nahi hua (${e.javaClass.simpleName}); app kholte hi chalu hoga")
        }
    }

    fun arm(c: Context) {
        val app = c.applicationContext
        try {
            val now = System.currentTimeMillis()
            val times = listOfNotNull(
                TraceRules.nextTraceAt(SmartPrefs.state(app), now, SmartPrefs.cfg(app)),
                TraceEngine.boostNextAt(app, now),
                nextDigestAt(app, now),
                if (Prefs.guardOn(app)) now + GuardRules.WATCHDOG_MS else null,
                if (SmartPrefs.heartbeatOn(app)) maxOf(now + 60_000L, SmartPrefs.heartbeatLast(app) + SmartPrefs.heartbeatHours(app) * 3_600_000L) else null,
            )
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = times.minOrNull()
            if (next == null) am.cancel(pi(app)) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi(app))
        } catch (e: Exception) {
            Prefs.log(app, "Smart alarm set nahi hua (${e.javaClass.simpleName})")
        }
    }

    /** Do whatever is due. Safe to call at any time. */
    fun tick(app: Context) {
        val now = System.currentTimeMillis()
        ensureGuard(app)
        try {
            val meta = DeviceInfo.read(app)
            if (SmartPrefs.traceOn(app)) TraceEngine.onBattery(app, meta.batteryPct, meta.charging)
            if (TraceEngine.boostDue(app, now)) TraceEngine.runBoost(app)
            val label = TelegramConfig.label(app)
            val saver = BatterySaver.active(app) // saves battery: skips extras only, never trace/alerts
            val recs = try { EventStore.readAll(app) } catch (_: Exception) { emptyList() }
            val dig = nextDigestAt(app, now - 24 * 3_600_000L)
            val day = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
            if (!saver && SmartPrefs.digestOn(app) && dig != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                val minNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                if (minNow >= SmartPrefs.digestMin(app) && SmartPrefs.digestLastDay(app) != day) {
                    SmartPrefs.setDigestLastDay(app, day)
                    val txt = Insights.digest(recs, now, recs.count { it.doneTo.isEmpty() }, meta.batteryPct)
                    EventRecorder.trigger(app, "Daily digest", label, PhotoMode.NONE, txt) {}
                }
            }
            if (!saver && SmartPrefs.heartbeatOn(app) && now - SmartPrefs.heartbeatLast(app) >= SmartPrefs.heartbeatHours(app) * 3_600_000L) {
                SmartPrefs.setHeartbeatLast(app, now)
                EventRecorder.trigger(app, "Heartbeat: guard chalu hai", label, PhotoMode.NONE, "Battery ${meta.batteryPct}%, network ${meta.network}.") {}
            }
        } catch (e: Exception) {
            Prefs.log(app, "Smart tick me dikkat (${e.javaClass.simpleName})")
        }
        arm(app)
    }
}

class SmartAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            try { SmartScheduler.tick(context.applicationContext) } finally { try { pending.finish() } catch (_: Exception) {} }
        }.start()
    }
}
