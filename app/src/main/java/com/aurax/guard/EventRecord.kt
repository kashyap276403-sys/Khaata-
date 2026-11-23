package com.aurax.guard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/** Google Maps link for a position. Works as plain text, no internet needed to create it. */
object MapsLink {
    fun of(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", lat, lon)
}

/**
 * One trigger (wrong password, test, snatch...) with everything known at that
 * moment. Saved in a local queue; [sent] is for the Telegram step later.
 */
data class EventRecord(
    val id: String,
    val timeMs: Long,
    val reason: String,
    val photoName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val locationNote: String = "",
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val network: String = "",
    val audioName: String? = null, // used in Version 2
    val sent: Boolean = false,
    val attempts: Int = 0, // Telegram send attempts so far
    val lastError: String = "", // why the last Telegram attempt failed (never contains the token)
    val nextTryMs: Long = 0, // do not retry before this time
    val doneTo: String = "", // comma list of Telegram targets already served (no duplicates)
    val aiSummary: String = "", // optional AI line, filled once
    val aiTries: Int = 0,
    val device: String = "", // device label when this alert was recorded (for filtering)
    val smart: String = "", // local smart engine line
    val risk: Int = -1, // 0 low, 1 medium, 2 high, -1 unknown
) {
    val mapsLink: String? get() = if (lat != null && lon != null) MapsLink.of(lat, lon) else null

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("t", timeMs)
        put("reason", reason)
        put("photo", photoName ?: JSONObject.NULL)
        put("lat", lat ?: JSONObject.NULL)
        put("lon", lon ?: JSONObject.NULL)
        put("acc", accuracyM?.toDouble() ?: JSONObject.NULL)
        put("locNote", locationNote)
        put("bat", batteryPct)
        put("chg", charging)
        put("net", network)
        put("audio", audioName ?: JSONObject.NULL)
        put("sent", sent)
        put("tgAtt", attempts)
        put("tgErr", lastError)
        put("tgNext", nextTryMs)
        put("tgDone", doneTo)
        put("ai", aiSummary)
        put("aiTry", aiTries)
        put("dev", device)
        put("sm", smart)
        put("rk", risk)
    }.toString()

    /** Plain-text report (what a Telegram message would contain later). */
    fun report(): String = buildString {
        appendLine("ALERT: $reason")
        appendLine("Samay: ${formatTime(timeMs)}")
        appendLine("Battery: " + (if (batteryPct >= 0) "$batteryPct%" else "pata nahi") + if (charging) " (charge ho raha)" else "")
        appendLine("Network: " + network.ifBlank { "pata nahi" })
        val link = mapsLink
        if (link != null) {
            appendLine("Location: $link")
            appendLine(locationNote + (accuracyM?.let { " (+-${it.toInt()} m)" } ?: ""))
        } else {
            appendLine("Location: nahi mili - $locationNote")
        }
        if (photoName != null) appendLine("Photo: $photoName")
        if (audioName != null) appendLine("Audio: $audioName")
    }.trimEnd()

    companion object {
        fun formatTime(ms: Long): String =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(ms))

        fun fromJson(s: String): EventRecord {
            val o = JSONObject(s)
            fun str(k: String) = if (o.isNull(k)) null else o.getString(k)
            fun dbl(k: String) = if (o.isNull(k)) null else o.getDouble(k)
            return EventRecord(
                id = o.getString("id"),
                timeMs = o.getLong("t"),
                reason = o.getString("reason"),
                photoName = str("photo"),
                lat = dbl("lat"),
                lon = dbl("lon"),
                accuracyM = dbl("acc")?.toFloat(),
                locationNote = o.optString("locNote", ""),
                batteryPct = o.optInt("bat", -1),
                charging = o.optBoolean("chg", false),
                network = o.optString("net", ""),
                audioName = str("audio"),
                sent = o.optBoolean("sent", false),
                attempts = o.optInt("tgAtt", 0),
                lastError = o.optString("tgErr", ""),
                nextTryMs = o.optLong("tgNext", 0L),
                doneTo = o.optString("tgDone", ""),
                aiSummary = o.optString("ai", ""),
                aiTries = o.optInt("aiTry", 0),
                device = o.optString("dev", ""),
                smart = o.optString("sm", ""),
                risk = o.optInt("rk", -1),
            )
        }
    }
}
