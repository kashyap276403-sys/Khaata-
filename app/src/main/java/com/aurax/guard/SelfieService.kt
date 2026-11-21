package com.aurax.guard

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Short-lived foreground service that takes one intruder photo, then stops. */
class SelfieService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(
                11,
                Notif.build(this, "Suraksha photo li ja rahi hai"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } catch (e: Exception) {
            Prefs.log(this, "Foreground start nahi hua: ${e.message}")
        }
        val label = intent?.getStringExtra("label") ?: ""
        SelfieCapture.run(this, label) { ok, msg ->
            Prefs.log(this, msg)
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
