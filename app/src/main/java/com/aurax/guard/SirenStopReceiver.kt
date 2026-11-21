package com.aurax.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Siren band karein" button in the siren notification (works only after unlock). */
class SirenStopReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.aurax.guard.STOP_SIREN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Siren.stop()
        Prefs.log(context, "Notification se siren band ki")
    }
}
