package com.aurax.guard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

/** Loud alarm sound that plays even if the phone is on silent. */
object Siren {
    private var player: MediaPlayer? = null
    private var appCtx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    @Synchronized
    fun start(context: Context, seconds: Int = 25): Boolean {
        if (player != null) return true
        val c = context.applicationContext
        appCtx = c
        return try {
            val am = c.getSystemService(AudioManager::class.java)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(c, uri)
                isLooping = true
                prepare()
                start()
            }
            player = mp
            try {
                c.getSystemService(android.app.NotificationManager::class.java)
                    .notify(13, Notif.sirenAlert(c))
            } catch (_: Exception) {}
            main.removeCallbacks(autoStop)
            main.postDelayed(autoStop, seconds * 1000L)
            true
        } catch (e: Exception) {
            Prefs.log(c, "Siren nahi baji: ${e.message}")
            false
        }
    }

    @Synchronized
    fun stop() {
        main.removeCallbacks(autoStop)
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
        try {
            appCtx?.getSystemService(android.app.NotificationManager::class.java)?.cancel(13)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun isPlaying() = player != null
}
