package com.aurax.guard.telegram

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aurax.guard.EventRecord
import com.aurax.guard.EventStore
import com.aurax.guard.Prefs
import java.util.concurrent.Executors

/**
 * Sends the local queue to Telegram, oldest first, ONE message per alert
 * (photo with caption, or plain text). Each success is saved immediately, so a
 * retry never sends the same alert twice. When offline or Telegram is busy the
 * alert stays in the queue and is retried automatically.
 */
object TelegramDispatcher {
    private const val JOB_ID = 4242
    private const val BATCH = 20
    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor()

    /** Try to send what is waiting, on a background thread. Safe to call any time. */
    fun kick(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        if (!TelegramConfig.configured(app)) return
        worker.execute {
            try {
                runOnce(app, force)
            } catch (e: Exception) {
                Prefs.log(app, "Telegram dispatcher error: ${e.javaClass.simpleName}")
            }
        }
    }

    fun isOnline(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true // unknown: just try
    }

    fun hasWaiting(ctx: Context): Boolean {
        if (!TelegramConfig.configured(ctx)) return false
        val since = TelegramConfig.sinceMs(ctx)
        return EventStore.readAll(ctx).any { TelegramRules.isWaiting(it, since) }
    }

    /** Ask Android to run us again when the internet is back (survives app kill and reboot). */
    fun schedule(ctx: Context) {
        try {
            val js = ctx.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, RetryJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(15_000)
                .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build()
            js.schedule(job)
        } catch (e: Exception) {
            Prefs.log(ctx, "Telegram retry job schedule nahi hui: ${e.javaClass.simpleName}")
        }
    }

    fun scheduleIfWaiting(ctx: Context) {
        try {
            if (hasWaiting(ctx)) schedule(ctx)
        } catch (_: Exception) {
        }
    }

    /** Blocking. One run at a time (lock), so two triggers can never send the same alert. */
    fun runOnce(app: Context, force: Boolean) {
        synchronized(lock) {
            if (!TelegramConfig.configured(app)) return
            val token = TelegramConfig.token(app)
            if (token == null) {
                TelegramConfig.setStatus(app, "Token padh nahi paya, dobara save karein")
                return
            }
            val chat = TelegramConfig.chatId(app)
            val label = TelegramConfig.label(app)
            val since = TelegramConfig.sinceMs(app)
            val now = System.currentTimeMillis()

            val due = EventStore.readAll(app).reversed()
                .filter { TelegramRules.isDue(it, since, now, force) }
                .take(BATCH)
            if (due.isEmpty()) {
                if (hasWaiting(app)) schedule(app)
                return
            }
            if (!isOnline(app)) {
                TelegramConfig.setStatus(app, "Offline: ${due.size} alert queue me ruke hain, internet aate hi jayenge")
                schedule(app)
                return
            }

            val targets = TelegramConfig.targets(app)
            val ids = targets.map { it.id }
            var sent = 0
            for (rec in due) {
                var done = TelegramRules.parseDone(rec.doneTo)
                val hasAudio = rec.audioName != null && com.aurax.guard.AudioClip.file(app, rec.audioName).exists()
                var stop = false
                for (t in targets) {
                    val primary = t.id == TelegramConfig.PRIMARY_ID
                    var res: TgResult = TgResult.Ok
                    if (!TelegramRules.msgDone(done, t.id)) {
                        res = sendRecord(app, t.token, t.chatId, label, rec)
                        if (res is TgResult.Ok) done = done + t.id
                    }
                    if (res is TgResult.Ok && hasAudio && !TelegramRules.audioDone(done, t.id)) {
                        res = sendAudio(app, t.token, t.chatId, label, rec)
                        if (res is TgResult.Ok) done = done + "${t.id}:a"
                    }
                    val r = res
                    val doneNow = TelegramRules.joinDone(done)
                    when (r) {
                        is TgResult.Ok -> {}
                        is TgResult.Retry -> {
                            val why = TelegramRules.redact(r.why, t.token)
                            EventStore.update(app, rec.id) {
                                it.copy(
                                    doneTo = doneNow,
                                    attempts = it.attempts + 1,
                                    lastError = why,
                                    nextTryMs = now + maxOf(r.afterMs, TelegramRules.backoffMs(it.attempts)),
                                )
                            }
                            TelegramConfig.setStatus(app, "Ruka hua: $why")
                            schedule(app)
                            return
                        }
                        is TgResult.Fail -> {
                            val why = TelegramRules.redact(r.why, t.token)
                            if (primary) {
                                EventStore.update(app, rec.id) {
                                    it.copy(doneTo = doneNow, attempts = it.attempts + 1, lastError = why,
                                        nextTryMs = now + TelegramRules.backoffMs(it.attempts))
                                }
                                TelegramConfig.setStatus(app, "Fail: $why")
                                if (r.global) return
                                stop = true
                            } else {
                                // A wrong extra bot must never block the queue: give up on that target only, and say so.
                                done = done + "${t.id}!"
                                TelegramConfig.setExtraStatus(app, t.id, "Fail: $why")
                            }
                        }
                    }
                    if (stop) break
                }
                if (stop) continue
                val fin = done
                if (TelegramRules.complete(fin, ids, hasAudio)) {
                    EventStore.update(app, rec.id) { it.copy(sent = true, doneTo = TelegramRules.joinDone(fin), lastError = "", nextTryMs = 0) }
                    sent++
                    targets.filter { it.id != TelegramConfig.PRIMARY_ID && TelegramRules.msgDone(fin, it.id) && "${it.id}!" !in fin }
                        .forEach { TelegramConfig.setExtraStatus(app, it.id, "OK: aakhri alert gaya") }
                } else {
                    EventStore.update(app, rec.id) { it.copy(doneTo = TelegramRules.joinDone(fin)) }
                }
            }
            if (sent > 0) {
                TelegramConfig.setStatus(app, "$sent alert bheje gaye")
                Prefs.log(app, "Telegram: $sent alert bheje gaye (device: $label)")
            }
            if (hasWaiting(app)) schedule(app)
        }
    }

    private fun sendAudio(app: Context, token: String, chat: String, label: String, rec: EventRecord): TgResult {
        val f = com.aurax.guard.AudioClip.file(app, rec.audioName ?: return TgResult.Ok)
        val bytes = try { f.readBytes() } catch (_: Exception) { return TgResult.Ok }
        return TelegramClient.sendAudioFile(token, chat, TelegramFormat.caption(label, rec).take(200) + "\n(Audio clip)", bytes, f.name, "audio/mp4")
    }

    private fun sendRecord(app: Context, token: String, chat: String, label: String, rec: EventRecord): TgResult {
        val name = rec.photoName
        if (name != null) {
            val bytes = PhotoReader.read(app, name)
            if (bytes != null) return TelegramClient.sendPhoto(token, chat, TelegramFormat.caption(label, rec), bytes)
            return TelegramClient.sendMessage(token, chat, TelegramFormat.photoMissing(label, rec))
        }
        return TelegramClient.sendMessage(token, chat, TelegramFormat.text(label, rec))
    }
}
