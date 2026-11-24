package com.aurax.guard.telegram

import com.aurax.guard.EventRecord

/** Message texts. Every one starts with "Device: <label>" so you can tell phones apart. */
object TelegramFormat {
    /** Telegram allows 1024 characters in a photo/audio caption. */
    const val CAPTION_MAX = 1000

    private fun aiLine(r: EventRecord) =
        (if (r.smart.isBlank()) "" else "\nSmart: " + r.smart) + (if (r.aiSummary.isBlank()) "" else "\nAI: " + r.aiSummary)

    private fun extraLine(r: EventRecord) = if (r.extra.isBlank()) "" else "\n" + r.extra

    fun text(label: String, r: EventRecord) = "Device: $label\n" + r.report() + extraLine(r) + aiLine(r)

    fun caption(label: String, r: EventRecord): String {
        val ai = aiLine(r)
        val head = "Device: $label\n" + r.report() + extraLine(r)
        val room = CAPTION_MAX - ai.length
        return (if (head.length > room) head.take(room - 3) + "..." else head) + ai
    }

    fun photoMissing(label: String, r: EventRecord) =
        text(label, r) + "\n(Photo ab phone me nahi hai, isliye sirf text bheja.)"

    fun test(label: String, nowMs: Long) =
        "Device: $label\nTEST ALERT\nSamay: ${EventRecord.formatTime(nowMs)}\nTelegram alerts sahi se chal rahe hain."
}
