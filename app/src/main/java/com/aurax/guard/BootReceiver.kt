package com.aurax.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts snatch protection after the phone reboots, if it was switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.snatchOn(context)) return
        try {
            context.startForegroundService(Intent(context, SnatchService::class.java))
        } catch (e: Exception) {
            Prefs.log(context, "Reboot ke baad snatch service shuru nahi hui: ${e.message}")
        }
    }
}
