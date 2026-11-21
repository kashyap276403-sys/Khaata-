package com.aurax.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

object Notif {
    private const val CHANNEL = "guard"

    fun build(c: Context, text: String): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "auraX Guard", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle("auraX Guard")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    /** Shown while the siren sounds. "Siren band karein" needs the phone unlocked. */
    fun sirenAlert(c: Context): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("siren", "Siren", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getBroadcast(
            c,
            1,
            Intent(c, SirenStopReceiver::class.java).setAction(SirenStopReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(c, R.drawable.ic_stat_guard),
            "Siren band karein",
            pi,
        ).apply {
            if (Build.VERSION.SDK_INT >= 31) setAuthenticationRequired(true)
        }.build()
        return Notification.Builder(c, "siren")
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle("Siren baj rahi hai")
            .setContentText("Band karne ke liye phone unlock karein")
            .setOngoing(true)
            .addAction(action)
            .build()
    }
}
