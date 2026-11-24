package com.aurax.guard

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
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
        val n = Prefs.bumpFailures(context)
        if (Prefs.sirenOn(context) && !com.aurax.guard.smart.SmartPrefs.stealth(context) && n >= 3 && !Prefs.snoozed(context)) {
            Siren.start(context)
            Prefs.log(context, "3 galat koshish: siren chalu")
        }
        if (n < Prefs.attemptsBeforePhoto(context)) return
        val label = "Galat password #$n"
        if (!Prefs.selfieOn(context)) {
            EventRecorder.trigger(context, label, label, EventRecorder.PhotoMode.NONE)
            return
        }
        // Record GPS/battery/network and pick up the photo that the chain below takes.
        EventRecorder.trigger(context, label, label, EventRecorder.PhotoMode.WAIT_FOR_EXTERNAL)
        Prefs.log(context, "$label. Photo li ja rahi hai...")

        val pending = goAsync()
        // 1) Best: the always-on guard already holds camera access.
        val svc = GuardService.instance
        if (svc != null && GuardService.cameraCapable) {
            svc.takeSelfie(label) { ok ->
                if (ok) pending.finish() else fallback(context, label, pending)
            }
        } else {
            fallback(context, label, pending)
        }
    }

    /** 2) Start a short camera service; 3) if Android refuses, use camera directly. */
    private fun fallback(context: Context, label: String, pending: BroadcastReceiver.PendingResult) {
        try {
            context.startForegroundService(
                Intent(context, SelfieService::class.java).putExtra("label", label)
            )
            pending.finish()
        } catch (e: Exception) {
            Prefs.log(context, "Service start nahi hui (${e.javaClass.simpleName}), seedha camera try")
            SelfieCapture.run(context, label) { _, msg ->
                Prefs.log(context, msg)
                pending.finish()
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        Prefs.resetFailures(context)
        if (Siren.isPlaying()) {
            Siren.stop()
            Prefs.log(context, "Owner ne unlock kiya: siren band")
        }
    }
}
