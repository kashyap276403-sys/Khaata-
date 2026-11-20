package com.aurax.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Android tells this receiver about every wrong lock-screen attempt, but only
 * while the app is an active Device Admin. Admin is also what lets us lock
 * the screen on a snatch.
 */
class GuardAdminReceiver : DeviceAdminReceiver() {

    @Deprecated("Android still calls this for wrong lock-screen attempts")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        if (!Prefs.selfieOn(context)) return
        val n = Prefs.bumpFailures(context)
        if (n < Prefs.attemptsBeforePhoto(context)) return
        Prefs.log(context, "Galat password ($n). Photo li ja rahi hai...")

        val pending = goAsync()
        try {
            context.startForegroundService(Intent(context, SelfieService::class.java))
            pending.finish()
        } catch (e: Exception) {
            // Android may refuse a background foreground-service start. Try the
            // camera directly while this broadcast is still alive.
            Prefs.log(context, "Service start nahi hui (${e.javaClass.simpleName}), seedha camera try")
            SelfieCapture(context).capture { ok, msg ->
                Prefs.log(context, msg)
                if (ok) Prefs.addPhoto(context)
                pending.finish()
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        Prefs.resetFailures(context)
    }
}
