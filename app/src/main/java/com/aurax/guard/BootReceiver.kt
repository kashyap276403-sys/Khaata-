package com.aurax.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the guard after the phone reboots, if it was switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try { com.aurax.guard.telegram.TelegramDispatcher.scheduleIfWaiting(context) } catch (_: Exception) {}
        try { SimWatcher.check(context) } catch (_: Exception) {}
        try { com.aurax.guard.telegram.TelegramDispatcher.kick(context) } catch (_: Exception) {}
        if (!Prefs.guardOn(context)) return
        try {
            GuardService.start(context, fromUi = false)
        } catch (e: Exception) {
            Prefs.log(context, "Reboot ke baad guard shuru nahi hua: ${e.message}")
        }
    }
}
