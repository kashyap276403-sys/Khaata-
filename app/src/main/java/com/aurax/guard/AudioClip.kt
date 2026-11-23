package com.aurax.guard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Records a 10-15 s clip when a trigger fires (optional, OFF by default).
 * Needs: the audio switch ON, microphone permission, and the guard started from
 * the app screen (Android only lets the microphone run in the background then).
 * Anything missing means "no clip", never a crash.
 */
object AudioClip {
    fun dir(ctx: Context) = File(ctx.filesDir, "audio").apply { mkdirs() }

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun canRecord(ctx: Context) = Prefs.audioOn(ctx) && hasPermission(ctx) && GuardService.micCapable

    /** Why no clip would be recorded right now (one line), or null if it can. */
    fun blocker(ctx: Context): String? = when {
        !Prefs.audioOn(ctx) -> "Audio clip band hai"
        !hasPermission(ctx) -> "Microphone permission nahi di"
        !GuardService.micCapable -> "Guard ko app kholkar dobara chalu karein (background me mic ke liye)"
        else -> null
    }

    fun file(ctx: Context, name: String) = File(dir(ctx), File(name).name)

    /** [done] gets the file name, or null if recording failed. Always called once, on the main thread. */
    fun record(ctx: Context, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        if (!canRecord(app)) { done(null); return }
        val name = "clip_${System.currentTimeMillis()}.m4a"
        val out = file(app, name)
        val main = Handler(Looper.getMainLooper())
        var rec: MediaRecorder? = null
        try {
            rec = MediaRecorder(app).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(22050)
                setAudioEncodingBitRate(48000)
                setMaxDuration(Prefs.audioSeconds(app) * 1000)
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Prefs.log(app, "Audio clip shuru nahi hui: ${e.javaClass.simpleName}")
            try { rec?.release() } catch (_: Exception) {}
            out.delete()
            done(null)
            return
        }
        main.postDelayed({
            var ok = true
            try { rec.stop() } catch (_: Exception) { ok = false }
            try { rec.release() } catch (_: Exception) {}
            if (ok && out.length() > 0) {
                Prefs.log(app, "Audio clip save hui (${Prefs.audioSeconds(app)} sec)")
                done(name)
            } else {
                out.delete()
                Prefs.log(app, "Audio clip khaali rahi, hata di")
                done(null)
            }
        }, Prefs.audioSeconds(app) * 1000L)
    }
}
