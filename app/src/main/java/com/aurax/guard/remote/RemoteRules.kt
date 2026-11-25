package com.aurax.guard.remote

import org.json.JSONObject

data class Cmd(val name: String, val arg: String)
data class Upd(val id: Long, val chatId: Long?, val fromId: Long?, val text: String, val dateSec: Long, val isPrivate: Boolean)
enum class Decision { RUN, DENY_MASTER_OFF, DENY_FEATURE_OFF, COOLDOWN, RATE_LIMIT, UNKNOWN }

/** Pure rules for Telegram remote control: parsing, who may command, limits, polling speed. Unit tested. */
object RemoteRules {
    val COMMANDS = listOf("loc", "photo", "status", "trace", "saver", "boost", "help")
    const val MAX_AGE_MS = 10 * 60_000L
    const val MAX_PER_HOUR = 20

    /** "/loc", "/loc@MyBot", "/saver on" -> Cmd. Anything else (plain text, unknown) -> null. */
    fun parse(text: String?): Cmd? {
        val t = text?.trim() ?: return null
        if (!t.startsWith("/")) return null
        val parts = t.split(Regex("\\s+"), limit = 2)
        val name = parts[0].removePrefix("/").substringBefore('@').lowercase()
        if (name.isEmpty() || name.length > 12) return null
        return Cmd(name, parts.getOrElse(1) { "" }.trim().lowercase().take(20))
    }

    /** Only the configured chat, and only a private chat with that same user. Groups and strangers are ignored. */
    fun authorized(u: Upd, configuredChat: String): Boolean {
        val cfg = configuredChat.trim().toLongOrNull() ?: return false
        return u.isPrivate && u.chatId == cfg && u.fromId == cfg
    }

    fun fresh(dateSec: Long, nowMs: Long): Boolean = dateSec > 0 && nowMs - dateSec * 1000L in -60_000L..MAX_AGE_MS

    fun cooldownMs(name: String): Long = when (name) {
        "photo" -> 60_000L
        "loc" -> 30_000L
        "trace" -> 60_000L
        "boost" -> 120_000L
        "saver" -> 5_000L
        else -> 10_000L // status, help
    }

    /** One decision for one command. [lastRunMs] is when this same command last ran (0 = never). */
    fun decide(
        c: Cmd, master: Boolean, locOn: Boolean, photoOn: Boolean, modeOn: Boolean,
        lastRunMs: Long, runsLastHour: Int, nowMs: Long,
    ): Decision {
        if (c.name !in COMMANDS) return Decision.UNKNOWN
        if (!master) return Decision.DENY_MASTER_OFF
        val featureOk = when (c.name) {
            "loc", "trace" -> locOn
            "photo" -> photoOn
            "status", "saver", "boost" -> modeOn
            else -> true // help
        }
        if (!featureOk) return Decision.DENY_FEATURE_OFF
        if (lastRunMs > 0 && nowMs - lastRunMs < cooldownMs(c.name)) return Decision.COOLDOWN
        if (runsLastHour >= MAX_PER_HOUR) return Decision.RATE_LIMIT
        return Decision.RUN
    }

    /** How often to look for new commands. Longer when the screen is off or Battery Saver is on; backs off when offline. */
    fun pollIntervalMs(screenOn: Boolean, charging: Boolean, saver: Boolean, failures: Int): Long {
        val base = when {
            saver && !charging -> if (screenOn) 5 * 60_000L else 15 * 60_000L
            screenOn || charging -> 60_000L
            else -> 5 * 60_000L
        }
        val back = 1L shl failures.coerceIn(0, 3)
        return (base * back).coerceAtMost(30 * 60_000L)
    }

    fun nextPollAt(lastPollMs: Long, nowMs: Long, intervalMs: Long): Long = maxOf(nowMs + 20_000L, lastPollMs + intervalMs)

    fun parseUpdates(body: String): List<Upd> = try {
        val arr = JSONObject(body).optJSONArray("result") ?: org.json.JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optLong("update_id", -1)
            if (id < 0) return@mapNotNull null
            val m = o.optJSONObject("message")
                ?: return@mapNotNull Upd(id, null, null, "", 0, false) // not a message: only advance the offset
            val chat = m.optJSONObject("chat")
            val from = m.optJSONObject("from")
            Upd(
                id, chat?.let { if (it.has("id")) it.getLong("id") else null }, from?.let { if (it.has("id")) it.getLong("id") else null },
                m.optString("text", ""), m.optLong("date", 0), chat?.optString("type") == "private",
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    data class StatusIn(
        val label: String, val battery: Int, val charging: Boolean, val guardOn: Boolean, val running: Boolean,
        val parkOn: Boolean, val stealth: Boolean, val saver: Boolean, val traceOn: Boolean, val traceActive: Boolean,
        val boostOn: Boolean, val queued: Int, val screenOn: Boolean, val network: String,
    )

    fun statusText(s: StatusIn): String = buildString {
        appendLine("Battery: " + (if (s.battery >= 0) "${s.battery}%" else "pata nahi") + if (s.charging) " (charge ho raha)" else "")
        appendLine("Thief Protection: " + (if (!s.guardOn) "OFF" else if (s.running) "ON, chal raha" else "ON par ruka hua"))
        appendLine("Park mode: ${if (s.parkOn) "ON" else "OFF"} | Stealth: ${if (s.stealth) "ON" else "OFF"}")
        appendLine("Battery saver: ${if (s.saver) "ON" else "OFF"}")
        appendLine("Emergency trace: " + (if (!s.traceOn) "OFF" else if (s.traceActive) "ON, trace chal raha" else "ON, tayyar") + " | Guard Boost: ${if (s.boostOn) "ON" else "OFF"}")
        appendLine("Queue me baaki: ${s.queued}")
        appendLine("Network: ${s.network.ifBlank { "pata nahi" }} | Screen: ${if (s.screenOn) "on" else "off"}")
        append("Phone online (ye jawab abhi bheja gaya).")
    }

    fun helpText(master: Boolean, loc: Boolean, photo: Boolean, mode: Boolean): String {
        fun f(b: Boolean) = if (b) "" else " [OFF]"
        return "Commands:\n" +
            "/loc - abhi location${f(loc)}\n/photo - photo + location${f(photo)}\n/trace - location + status + trail${f(loc)}\n" +
            "/status - battery, guards, queue${f(mode)}\n/saver on|off - battery saver${f(mode)}\n/boost - ek extra snapshot${f(mode)}\n/help - ye list\n" +
            "Jawab phone ke polling samay par aata hai (1-15 min). Koi command file delete ya protection band nahi kar sakta."
    }
}
