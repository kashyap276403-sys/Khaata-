package com.aurax.guard

import android.content.Context
import android.telephony.TelephonyManager

/** Checks the SIM and records an alert (goes to Telegram through the queue) when it changes. No permission needed. */
object SimWatcher {
    @Synchronized
    fun check(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val tm = app.getSystemService(TelephonyManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val slots = tm.phoneCount.coerceIn(1, 2)
            val states = (0 until slots).map { tm.getSimState(it) }
            val fp = SimRules.fingerprint(states, tm.simOperator ?: "") ?: return
            val prev = Prefs.simFingerprint(app)
            if (prev.isEmpty()) { Prefs.setSimFingerprint(app, fp); return }
            val msg = SimRules.change(prev, fp) ?: return
            val now = System.currentTimeMillis()
            Prefs.setSimFingerprint(app, fp)
            if (!Prefs.simAlertOn(app) || !SimRules.cooledDown(Prefs.simLastAlertMs(app), now)) return
            Prefs.setSimLastAlertMs(app, now)
            Prefs.log(app, "SIM alert: $msg")
            EventRecorder.trigger(app, "SIM ALERT: $msg", "SIM", EventRecorder.PhotoMode.NONE)
        } catch (e: Exception) {
            Prefs.log(app, "SIM check nahi hua: ${e.javaClass.simpleName}")
        }
    }

    /** Remember today's SIM as "normal" (called when the guard is first turned on). */
    fun remember(ctx: Context) {
        try {
            val tm = ctx.getSystemService(TelephonyManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val states = (0 until tm.phoneCount.coerceIn(1, 2)).map { tm.getSimState(it) }
            SimRules.fingerprint(states, tm.simOperator ?: "")?.let { Prefs.setSimFingerprint(ctx, it) }
        } catch (_: Exception) {
        }
    }
}
