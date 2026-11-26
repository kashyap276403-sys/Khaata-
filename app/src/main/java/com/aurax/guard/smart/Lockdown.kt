package com.aurax.guard.smart

import android.content.Context
import com.aurax.guard.AlertType
import com.aurax.guard.GuardService
import com.aurax.guard.Prefs

/** Android side of Tactical Lockdown. Runs from the app (fromUi = true) or an authorized Telegram command. */
object Lockdown {
    fun active(c: Context) = SmartPrefs.lockdownOn(c)

    private fun read(c: Context) = LockSnap(
        Prefs.chargerAlarmOn(c), Prefs.simAlertOn(c), Prefs.alertTypeOn(c, AlertType.WRONG_UNLOCK.key), SmartPrefs.stealth(c),
        SmartPrefs.tracePhoto(c), SmartPrefs.traceOn(c), SmartPrefs.saverAuto(c), Prefs.batteryAlertOn(c), SmartPrefs.ignoreCharging(c),
    )

    private fun write(c: Context, s: LockSnap) {
        Prefs.setChargerAlarmOn(c, s.charger); Prefs.setSimAlertOn(c, s.sim)
        Prefs.setAlertTypeOn(c, AlertType.WRONG_UNLOCK.key, s.wrongUnlock); SmartPrefs.setStealth(c, s.stealth)
        SmartPrefs.setTracePhoto(c, s.tracePhoto); SmartPrefs.setTraceOn(c, s.traceOn); SmartPrefs.setSaverAuto(c, s.saverAuto)
        Prefs.setBatteryAlertOn(c, s.batteryAlert); SmartPrefs.setIgnoreCharging(c, s.ignoreCharging)
    }

    @Synchronized
    fun enter(ctx: Context, fromUi: Boolean) {
        val c = ctx.applicationContext
        SmartPrefs.setLockdownSaved(c, LockdownRules.snapshotToKeep(active(c), SmartPrefs.lockdownSaved(c), read(c)))
        write(c, LockdownRules.TARGET)
        Prefs.setSnatchLockOn(c, true)
        Prefs.setGuardOn(c, true)
        SmartPrefs.setLockdownOn(c, true)
        Prefs.log(c, "Tactical Lockdown chalu")
        refresh(c, fromUi)
    }

    /** Restores the saved settings. Never switches Thief Protection (guard / jhatka lock) off. */
    @Synchronized
    fun exit(ctx: Context, fromUi: Boolean) {
        val c = ctx.applicationContext
        write(c, LockdownRules.restoreTarget(SmartPrefs.lockdownSaved(c)))
        SmartPrefs.setLockdownOn(c, false)
        SmartPrefs.setLockdownSaved(c, "")
        Prefs.log(c, "Tactical Lockdown band (settings wapas)")
        refresh(c, fromUi)
    }

    private fun refresh(c: Context, fromUi: Boolean) {
        try { GuardService.start(c, fromUi) } catch (_: Exception) {}
        try { SmartScheduler.arm(c) } catch (_: Exception) {}
    }

    fun text(c: Context) = if (active(c)) "ON: tamper/SIM/USB, wrong-password photo, stealth, low-battery survival" else "OFF"
}
