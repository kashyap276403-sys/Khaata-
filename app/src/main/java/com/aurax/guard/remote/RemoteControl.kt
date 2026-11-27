package com.aurax.guard.remote

import android.content.Context
import android.os.PowerManager
import com.aurax.guard.DeviceInfo
import com.aurax.guard.EventRecorder
import com.aurax.guard.EventRecorder.PhotoMode
import com.aurax.guard.EventStore
import com.aurax.guard.GuardService
import com.aurax.guard.Prefs
import com.aurax.guard.smart.BatterySaver
import com.aurax.guard.smart.SmartPrefs
import com.aurax.guard.smart.TraceEngine
import com.aurax.guard.telegram.TelegramConfig
import com.aurax.guard.telegram.TelegramDispatcher
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telegram remote control. Looks for commands with short getUpdates calls on a slow, adaptive schedule
 * (no long-poll, no wake lock, no service of its own). Only the configured private chat may command it.
 * Replies go through the normal alert queue, so they carry the Device label and survive being offline.
 * There is NO command that deletes files or turns Thief Protection off.
 */
object RemoteControl {
    @Volatile private var running = false

    private fun screenOn(app: Context) = try { app.getSystemService(PowerManager::class.java).isInteractive } catch (_: Exception) { true }

    private fun active(app: Context) = RemotePrefs.masterOn(app) && TelegramConfig.configured(app)

    fun interval(app: Context): Long {
        val m = DeviceInfo.read(app)
        return RemoteRules.pollIntervalMs(screenOn(app), m.charging, BatterySaver.active(app), RemotePrefs.failures(app))
    }

    /** When the alarm should look next (null when remote control is off). */
    fun nextPollAt(app: Context, now: Long): Long? {
        if (!active(app)) return null
        val n = RemoteRules.nextPollAt(RemotePrefs.lastPollMs(app), now, interval(app))
        val r = RemotePrefs.retryAt(app)
        return if (r > 0) minOf(n, maxOf(r, now + 15_000L)) else n
    }

    /** One follow-up after a slow/stale location (only once per command, battery-light). */
    private fun runRetryIfDue(app: Context, now: Long) {
        val at = RemotePrefs.retryAt(app)
        if (at <= 0 || now < at) return
        val cmd = RemotePrefs.retryCmd(app)
        RemotePrefs.setRetry(app, 0L, "")
        if (!RemotePrefs.masterOn(app) || !RemotePrefs.locOn(app) || cmd.isEmpty()) return
        Prefs.log(app, "Remote /$cmd: location dobara try (pehle live fix nahi mila tha)")
        EventRecorder.trigger(app, "Emergency trace: remote /$cmd retry", label(app), PhotoMode.NONE, "Pehli location purani/adhoori thi, ye dobara koshish hai.") {}
    }

    fun due(app: Context, now: Long) = active(app) && now - RemotePrefs.lastPollMs(app) >= interval(app)

    /** Call from a background thread only. Never throws. */
    fun poll(app: Context, force: Boolean = false) {
        if (!active(app) || running) return
        val now = System.currentTimeMillis()
        try { runRetryIfDue(app, now) } catch (_: Exception) {}
        if (!force && !due(app, now)) return
        running = true
        try {
            val token = TelegramConfig.token(app) ?: return
            val chat = TelegramConfig.chatId(app)
            val body = getUpdates(token, RemotePrefs.offset(app))
            RemotePrefs.setLastPollMs(app, now)
            if (body == null) { RemotePrefs.setFailures(app, RemotePrefs.failures(app) + 1); return }
            RemotePrefs.setFailures(app, 0)
            val ups = RemoteRules.parseUpdates(body)
            if (ups.isEmpty()) return
            RemotePrefs.setOffset(app, ups.maxOf { it.id } + 1) // confirm first: a crash never replays a command
            if (!RemotePrefs.synced(app)) { // first poll after turning ON: drop the old backlog
                RemotePrefs.setSynced(app, true)
                Prefs.log(app, "Remote control tayyar (purane ${ups.size} messages ignore kiye)")
                return
            }
            for (u in ups) handle(app, u, chat, now)
        } catch (e: Exception) {
            Prefs.log(app, "Remote poll me dikkat (${e.javaClass.simpleName})")
        } finally {
            running = false
        }
    }

    private fun getUpdates(token: String, offset: Long): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = "https://api.telegram.org/bot$token/getUpdates?timeout=0&limit=10&offset=$offset&allowed_updates=%5B%22message%22%5D"
            conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun handle(app: Context, u: Upd, chat: String, now: Long) {
        if (u.text.isBlank()) return
        if (!RemoteRules.authorized(u, chat)) {
            if (now - RemotePrefs.lastStrangerMs(app) > 10 * 60_000L) {
                RemotePrefs.setLastStrangerMs(app, now)
                Prefs.log(app, "Remote: anjaan chat/user ka message rok diya (koi jawab nahi diya)")
            }
            return
        }
        if (!RemoteRules.fresh(u.dateSec, now)) return // old message: never run
        val cmd = RemoteRules.parse(u.text) ?: return
        val d = RemoteRules.decide(
            cmd, RemotePrefs.masterOn(app), RemotePrefs.locOn(app), RemotePrefs.photoOn(app), RemotePrefs.modeOn(app),
            RemotePrefs.lastRun(app, cmd.name), RemotePrefs.runsLastHour(app, now), now,
        )
        if (d != Decision.RUN) {
            Prefs.log(app, "Remote /${cmd.name}: $d")
            if (d == Decision.DENY_FEATURE_OFF || d == Decision.UNKNOWN) quickReply(app, now, "Command nahi chali", "/${cmd.name} abhi band ya anjaan hai. /help dekhein.")
            return
        }
        RemotePrefs.setLastRun(app, cmd.name, now)
        RemotePrefs.addRun(app, now)
        run(app, cmd)
    }

    private fun label(app: Context) = TelegramConfig.label(app)

    /** Replies about a refusal are limited to one per minute so nobody can make the phone spam. */
    private fun quickReply(app: Context, now: Long, title: String, text: String) {
        if (now - RemotePrefs.lastDenyMs(app) < 60_000L) return
        RemotePrefs.setLastDenyMs(app, now)
        reply(app, title, text)
    }

    private fun reply(app: Context, title: String, text: String) {
        EventRecorder.trigger(app, "Remote: $title", label(app), PhotoMode.NONE, text) {}
    }

    private fun run(app: Context, cmd: Cmd) {
        Prefs.log(app, "Remote /${cmd.name} ${cmd.arg} chali")
        try {
            when (cmd.name) {
                "help" -> reply(app, "/help", RemoteRules.helpText(true, RemotePrefs.locOn(app), RemotePrefs.photoOn(app), RemotePrefs.modeOn(app)))
                "status" -> reply(app, "/status", status(app))
                "loc" -> EventRecorder.trigger(app, "Emergency trace: remote /loc", label(app), PhotoMode.NONE, "Remote /loc: abhi ki location.") { scheduleRetry(app, "loc", it) }
                "trace" -> EventRecorder.trigger(app, "Emergency trace: remote /trace", label(app), PhotoMode.NONE, "Remote /trace:\n" + status(app)) { scheduleRetry(app, "trace", it) }
                "photo" -> {
                    val can = GuardService.cameraCapable
                    EventRecorder.trigger(
                        app, "Emergency trace: remote /photo", label(app), if (can) PhotoMode.CAPTURE_NOW else PhotoMode.NONE,
                        if (can) "Remote /photo." else "Remote /photo: photo nahi li (camera abhi available nahi, phone locked/guard app se chalu nahi). Location bheji.",
                    ) {}
                }
                "boost" -> TraceEngine.remoteSnapshot(app)
                "lockdown" -> { com.aurax.guard.smart.Lockdown.enter(app, false); reply(app, "/lockdown", "Tactical Lockdown ON.\n" + status(app)) }
                "normal" -> { com.aurax.guard.smart.Lockdown.exit(app, false); reply(app, "/normal", "Lockdown OFF, purani settings wapas. Thief Protection chalu hai.\n" + status(app)) }
                "saver" -> when (cmd.arg) {
                    "on", "off" -> {
                        SmartPrefs.setSaverManual(app, cmd.arg == "on")
                        Prefs.log(app, "Remote: Battery saver ${cmd.arg.uppercase()}")
                        reply(app, "/saver ${cmd.arg}", "Battery saver ab " + (if (BatterySaver.active(app)) "ON" else "OFF") + ".\n" + status(app))
                    }
                    else -> reply(app, "/saver", "Aise likhein: /saver on ya /saver off")
                }
            }
        } catch (e: Exception) {
            Prefs.log(app, "Remote /${cmd.name} fail (${e.javaClass.simpleName}); protection chalu hai")
        }
        try { TelegramDispatcher.kick(app) } catch (_: Exception) {}
    }

    private fun scheduleRetry(app: Context, cmd: String, rec: com.aurax.guard.EventRecord) {
        try {
            if (RemoteRules.staleFix(rec.lat, rec.locationNote) && RemotePrefs.retryAt(app) == 0L) {
                RemotePrefs.setRetry(app, System.currentTimeMillis() + RemoteRules.RETRY_MS, cmd)
                com.aurax.guard.smart.SmartScheduler.arm(app)
            }
        } catch (_: Exception) {}
    }

    fun status(app: Context): String {
        val m = DeviceInfo.read(app)
        return RemoteRules.statusText(RemoteRules.StatusIn(
            label(app), m.batteryPct, m.charging, Prefs.guardOn(app), GuardService.instance != null, Prefs.parkOn(app),
            SmartPrefs.stealth(app), BatterySaver.active(app), SmartPrefs.traceOn(app), SmartPrefs.state(app).active,
            SmartPrefs.boostOn(app), try { EventStore.pendingCount(app) } catch (_: Exception) { 0 }, screenOn(app), m.network,
        ))
    }
}
