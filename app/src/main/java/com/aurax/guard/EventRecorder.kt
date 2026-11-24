package com.aurax.guard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Collects photo + GPS + battery + network + time for one trigger and saves it
 * to the local queue. It never changes what the existing triggers already do;
 * it only records around them.
 */
object EventRecorder {
    enum class PhotoMode {
        /** No photo for this trigger. */
        NONE,

        /** Take the photo here (Test Trigger, wrong App PIN). */
        CAPTURE_NOW,

        /** Another part of the app already takes the photo; just find it afterwards. */
        WAIT_FOR_EXTERNAL,
    }

    private const val WAIT_PHOTO_MS = 14_000L
    private const val HARD_LIMIT_MS = 26_000L

    fun trigger(
        ctx: Context,
        reason: String,
        label: String,
        photo: PhotoMode,
        extra: String? = null,
        onDone: ((EventRecord) -> Unit)? = null,
    ) {
        val app = ctx.applicationContext
        if (!AlertType.allowed(app, reason)) {
            Prefs.log(app, "Alert band hai (is type ke liye): $reason")
            return
        }
        val main = Handler(Looper.getMainLooper())
        val startMs = System.currentTimeMillis()
        val meta = DeviceInfo.read(app)

        // Keep the CPU awake while we wait for GPS and the camera.
        val wake = try {
            app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "auraXGuard:event")
                .apply { acquire(HARD_LIMIT_MS + 4_000) }
        } catch (_: Exception) {
            null
        }

        val fixRef = AtomicReference<LocationFetcher.Fix?>(null)
        val locNoteRef = AtomicReference("")
        val photoRef = AtomicReference<String?>(null)
        val wantAudio = AudioClip.canRecord(app)
        val audioRef = AtomicReference<String?>(null)
        val parts = AtomicInteger((if (photo == PhotoMode.NONE) 1 else 2) + (if (wantAudio) 1 else 0))
        val finished = AtomicBoolean(false)

        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            val trail = try {
                if (AlertType.classify(reason) == AlertType.TRACE) {
                    val fx = fixRef.get()
                    val old = EventStore.readAll(app).filter { it.lat != null && it.lon != null }.map { com.aurax.guard.smart.Pt(it.timeMs, it.lat!!, it.lon!!) }
                    com.aurax.guard.smart.TraceRules.note(fx?.let { com.aurax.guard.smart.Pt(startMs, it.lat, it.lon) }, old, startMs)
                } else ""
            } catch (_: Exception) { "" }
            val extraText = listOf(extra ?: "", trail).filter { it.isNotBlank() }.joinToString("\n")
            val rec0 = EventRecord(
                id = UUID.randomUUID().toString(),
                timeMs = startMs,
                reason = reason,
                photoName = photoRef.get(),
                audioName = audioRef.get(),
                device = try { com.aurax.guard.telegram.TelegramConfig.label(app) } catch (_: Exception) { "" },
                lat = fixRef.get()?.lat,
                lon = fixRef.get()?.lon,
                accuracyM = fixRef.get()?.accuracyM,
                locationNote = locNoteRef.get(),
                batteryPct = meta.batteryPct,
                charging = meta.charging,
                network = meta.network,
                extra = extraText,
            )
            var rec = rec0
            try {
                if (Prefs.smartOn(app)) {
                    val earlier = try { EventStore.readAll(app) } catch (_: Exception) { emptyList() }
                    val a = com.aurax.guard.smart.RiskEngine.assess(
                        com.aurax.guard.smart.RiskEngine.inputFor(rec0, earlier, com.aurax.guard.ai.AiConfig.lang(app) == com.aurax.guard.ai.AiLang.ENGLISH),
                    )
                    rec = rec0.copy(smart = a.line(), risk = a.risk.ordinal, threat = a.threat)
                }
            } catch (_: Exception) {
            }
            try {
                EventStore.append(app, rec)
                try { com.aurax.guard.smart.AutoTuner.maybeAuto(app) } catch (_: Exception) {}
                Prefs.log(
                    app,
                    "Queue me save: $reason | " +
                        (rec.mapsLink ?: "location nahi") + " | " +
                        "${rec.batteryPct}% | ${rec.network}" +
                        (if (rec.photoName != null) " | photo ok" else ""),
                )
            } catch (e: Exception) {
                Prefs.log(app, "Queue me save nahi hua: ${e.message}")
            }
            try { if (wake?.isHeld == true) wake.release() } catch (_: Exception) {}
            try { com.aurax.guard.telegram.TelegramDispatcher.kick(app) } catch (_: Exception) {}
            try { com.aurax.guard.smart.TraceEngine.afterEvent(app, rec) } catch (_: Exception) {}
            main.post { onDone?.invoke(rec) }
        }

        fun partDone() {
            if (parts.decrementAndGet() <= 0) finish()
        }

        main.postDelayed({ finish() }, HARD_LIMIT_MS)

        LocationFetcher.fetch(app) { f, note ->
            fixRef.set(f)
            locNoteRef.set(note)
            partDone()
        }

        if (wantAudio) AudioClip.record(app) { n -> audioRef.set(n); partDone() }

        when (photo) {
            PhotoMode.NONE -> {}
            PhotoMode.CAPTURE_NOW -> SelfieCapture.runNamed(app, label) { ok, msg, name ->
                Prefs.log(app, msg)
                if (ok) photoRef.set(name)
                partDone()
            }
            PhotoMode.WAIT_FOR_EXTERNAL -> {
                val poll = object : Runnable {
                    var waited = 0L
                    override fun run() {
                        val p = SelfieCapture.listPhotos(app).firstOrNull { it.timeMs >= startMs - 2_000 }
                        if (p != null) {
                            photoRef.set(p.name)
                            partDone()
                        } else if (waited >= WAIT_PHOTO_MS) {
                            partDone()
                        } else {
                            waited += 1_000
                            main.postDelayed(this, 1_000)
                        }
                    }
                }
                main.postDelayed(poll, 1_500)
            }
        }
    }
}
