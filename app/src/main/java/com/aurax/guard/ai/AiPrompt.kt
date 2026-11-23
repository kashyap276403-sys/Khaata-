package com.aurax.guard.ai

/** What the AI is allowed to see about one alert. No photo, no audio, no coordinates. */
data class EventFacts(
    val type: String,
    val device: String,
    val batteryPct: Int,
    val charging: Boolean,
    val network: String,
    val time: String,
    val locationAccuracyM: Int?,
    val hasLocation: Boolean,
    val mapsLink: String?, // only filled if the user allowed it
    val triggersLast24h: Int,
)

enum class AiLang(val key: String, val title: String, val instruction: String) {
    HINGLISH("hinglish", "Simple Hinglish", "Write in simple Hinglish (Hindi words in English letters)."),
    HINDI("hindi", "Hindi", "Write in simple Hindi (Devanagari script)."),
    ENGLISH("english", "English", "Write in simple English.");

    companion object {
        fun of(key: String) = values().firstOrNull { it.key == key } ?: HINGLISH
    }
}

enum class AiStyle(val key: String, val title: String, val words: Int) {
    SHORT("short", "Short (1-2 lines)", 30),
    DETAIL("detail", "Detail (3-4 lines)", 70);

    companion object {
        fun of(key: String) = values().firstOrNull { it.key == key } ?: SHORT
    }
}

/** Builds the texts sent to the AI and cleans what comes back. Pure, unit tested. */
object AiPrompt {
    const val MAX_SUMMARY = 320

    private const val RULES =
        "You help a phone owner understand an anti-theft alert from their own phone. " +
            "Use ONLY the facts given. Do not invent places, people or causes. " +
            "Say what happened, how serious it looks, and one calm next step. " +
            "Never tell the user to confront anyone. Plain text only, no markdown, no emojis."

    fun alertSystem(lang: AiLang, style: AiStyle) = "$RULES ${lang.instruction} Use at most ${style.words} words."

    fun alertUser(f: EventFacts): String = buildString {
        append("Event: ").append(f.type).append('\n')
        append("Device: ").append(f.device).append('\n')
        append("Time: ").append(f.time).append('\n')
        append("Battery: ").append(if (f.batteryPct >= 0) "${f.batteryPct}%" else "unknown").append(if (f.charging) " (charging)" else "").append('\n')
        append("Network: ").append(f.network.ifBlank { "unknown" }).append('\n')
        append("Location: ").append(if (f.hasLocation) "available" + (f.locationAccuracyM?.let { ", accuracy about $it m" } ?: "") else "not available").append('\n')
        if (f.mapsLink != null) append("Map link: ").append(f.mapsLink).append('\n')
        append("Alerts from this phone in last 24 hours: ").append(f.triggersLast24h)
    }

    fun fileSystem(lang: AiLang) =
        "$RULES You explain why a file might deserve a look before the owner decides. " +
            "You never decide, never say 'delete it', and never claim a file is malware: say 'could be' and 'check'. " +
            "${lang.instruction} Use at most 45 words."

    fun fileUser(name: String, sizeText: String, reasons: List<String>) =
        "File name: $name\nSize: $sizeText\nWarning signs found by the app: ${reasons.joinToString("; ")}"

    fun hintsSystem(lang: AiLang) =
        "$RULES You turn a list of settings hints into short practical advice for Android phones (vivo/iQOO OriginOS or Funtouch OS, Motorola Hello UI). " +
            "${lang.instruction} Use at most 70 words."

    fun hintsUser(hints: List<String>) = "Phone check results:\n" + hints.joinToString("\n") { "- $it" }

    /** One short plain paragraph, safe to paste into Telegram. */
    fun sanitize(raw: String, max: Int = MAX_SUMMARY): String {
        var t = raw.replace(Regex("[*_`#>]+"), "").replace(Regex("\\s*\\n\\s*"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.length > max) t = t.take(max - 3).trimEnd() + "..."
        return t
    }

    fun redact(text: String, key: String?) = if (key.isNullOrBlank()) text else text.replace(key, "[key]")
}
