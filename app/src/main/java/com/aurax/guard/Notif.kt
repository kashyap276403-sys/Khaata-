package com.aurax.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notif {
    private const val CHANNEL = "guard"

    fun build(c: Context, text: String): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "auraX Guard", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("auraX Guard")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }
}
