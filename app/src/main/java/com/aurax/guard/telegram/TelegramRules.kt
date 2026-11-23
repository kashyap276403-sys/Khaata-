package com.aurax.guard.telegram

import com.aurax.guard.EventRecord
import kotlin.math.min

/** Pure rules for Telegram settings and retry timing. Unit tested, no Android classes. */
object TelegramRules {
    const val MAX_ATTEMPTS = 8

    private val TOKEN = Regex("^\\d{6,12}:[A-Za-z0-9_-]{30,}$")
    private val CHAT = Regex("^-?\\d{5,20}$")

    fun cleanToken(s: String) = s.trim().replace(Regex("\\s+"), "")
    fun isValidToken(s: String) = TOKEN.matches(cleanToken(s))

    fun cleanChatId(s: String) = s.trim()
    fun isValidChatId(s: String) = CHAT.matches(cleanChatId(s))

    /** One short line, max 24 characters. Falls back to the phone model name. */
    fun cleanLabel(s: String, fallback: String): String {
        val t = s.trim().replace(Regex("[\\r\\n]+"), " ").take(24).trim()
        return t.ifBlank { fallback.trim().take(24).ifBlank { "Phone" } }
    }

    /** 30s, 60s, 2m ... capped at 1 hour. */
    fun backoffMs(attempts: Int): Long = min(30_000L * (1L shl attempts.coerceIn(0, 10)), 3_600_000L)

    /** Is this entry waiting to go out now? [since] = when Telegram was switched on. */
    fun isDue(r: EventRecord, since: Long, now: Long, force: Boolean): Boolean =
        !r.sent && r.timeMs >= since && r.attempts < MAX_ATTEMPTS && (force || r.nextTryMs <= now)

    fun isWaiting(r: EventRecord, since: Long): Boolean =
        !r.sent && r.timeMs >= since && r.attempts < MAX_ATTEMPTS

    fun isFailed(r: EventRecord, since: Long): Boolean =
        !r.sent && r.timeMs >= since && r.attempts >= MAX_ATTEMPTS

    // ---- per-target delivery bookkeeping (no duplicates when several bots/chats are set) ----
    fun parseDone(s: String): Set<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun joinDone(s: Set<String>) = s.sorted().joinToString(",")

    /** "t" = message delivered, "t:a" = audio delivered, "t!" = gave up on this target (bad extra bot). */
    fun msgDone(done: Set<String>, t: String) = t in done || "$t!" in done
    fun audioDone(done: Set<String>, t: String) = "$t:a" in done || "$t!" in done

    fun complete(done: Set<String>, targets: List<String>, hasAudio: Boolean) =
        targets.all { msgDone(done, it) && (!hasAudio || audioDone(done, it)) }

    /** Never let the bot token appear in logs or on screen. */
    fun redact(text: String, token: String?): String =
        if (token.isNullOrBlank()) text else text.replace(token, "[token]")

    /** One plain line for an alert card. */
    fun recordStatus(r: EventRecord, configured: Boolean, since: Long): String = when {
        r.sent -> "Telegram: bhej di gayi"
        !configured -> "Telegram: band hai"
        r.timeMs < since -> "Telegram: purani entry (chalu hone se pehle ki), nahi bheji jayegi"
        r.attempts >= MAX_ATTEMPTS -> "Telegram: bhejna fail (${r.lastError}). Settings se 'dobara bhejein' dabayein"
        r.attempts > 0 -> "Telegram: ruka hua, ${r.attempts} koshish (${r.lastError}). Apne aap dobara try hoga"
        else -> "Telegram: bhejna baaki (internet aate hi jayega)"
    }
}
