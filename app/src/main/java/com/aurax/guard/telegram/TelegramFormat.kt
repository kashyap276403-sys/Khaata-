package com.aurax.guard.telegram

import com.aurax.guard.EventRecord

/** Message texts. Every one starts with "Device: <label>" so you can tell phones apart. */
object TelegramFormat {
    /** Telegram allows 1024 characters in a photo/audio caption. */
    const val CAPTION_MAX = 1000

    fun text(label: String, r: EventRecord) = "Device: $label\n" + r.report()

    fun caption(label: String, r: EventRecord): String {
        val t = text(label, r)
        return if (t.length > CAPTION_MAX) t.take(CAPTION_MAX - 3) + "..." else t
    }

    fun photoMissing(label: String, r: EventRecord) =
        text(label, r) + "\n(Photo ab phone me nahi hai, isliye sirf text bheja.)"

    fun test(label: String, nowMs: Long) =
        "Device: $label\nTEST ALERT\nSamay: ${EventRecord.formatTime(nowMs)}\nTelegram alerts sahi se chal rahe hain."
}
