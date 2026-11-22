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

            var sent = 0
            for (rec in due) {
                when (val res = sendRecord(app, token, chat, label, rec)) {
                    is TgResult.Ok -> {
                        EventStore.update(app, rec.id) { it.copy(sent = true, lastError = "", nextTryMs = 0) }
                        sent++
                    }
                    is TgResult.Retry -> {
                        val why = TelegramRules.redact(res.why, token)
                        EventStore.update(app, rec.id) {
                            it.copy(
                                attempts = it.attempts + 1,
                                lastError = why,
                                nextTryMs = now + maxOf(res.afterMs, TelegramRules.backoffMs(it.attempts)),
                            )
                        }
                        TelegramConfig.setStatus(app, "Ruka hua: $why")
                        schedule(app)
                        return
                    }
                    is TgResult.Fail -> {
                        val why = TelegramRules.redact(res.why, token)
                        EventStore.update(app, rec.id) {
                            it.copy(attempts = it.attempts + 1, lastError = why, nextTryMs = now + TelegramRules.backoffMs(it.attempts))
                        }
                        TelegramConfig.setStatus(app, "Fail: $why")
                        if (res.global) return
                    }
                }
            }
            if (sent > 0) {
                TelegramConfig.setStatus(app, "$sent alert bheje gaye")
                Prefs.log(app, "Telegram: $sent alert bheje gaye (device: $label)")
            }
            if (hasWaiting(app)) schedule(app)
        }
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
