package com.aurax.guard.telegram

import com.aurax.guard.EventRecord

enum class BadgeKind { SENT, QUEUED, OFFLINE, ERROR, LOCAL }
data class Badge(val kind: BadgeKind, val label: String)

/** One clear status pill per alert. Pure logic, unit tested. */
object Badges {
    private val PERMANENT = listOf("BOT_TOKEN galat", "CHAT_ID galat", "Bot is chat", "mana kiya")

    fun forRecord(r: EventRecord, configured: Boolean, since: Long, online: Boolean): Badge = when {
        r.sent -> Badge(BadgeKind.SENT, "Sent")
        !configured || r.timeMs < since -> Badge(BadgeKind.LOCAL, "Local only")
        r.attempts >= TelegramRules.MAX_ATTEMPTS || PERMANENT.any { r.lastError.contains(it) } -> Badge(BadgeKind.ERROR, "Error")
        !online -> Badge(BadgeKind.OFFLINE, "Offline")
        else -> Badge(BadgeKind.QUEUED, "Queued")
    }
}
