package com.aurax.guard.smart

import com.aurax.guard.AlertType
import com.aurax.guard.EventRecord
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Risk(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/** One earlier alert, reduced to what the rules need. */
data class Hist(val timeMs: Long, val type: AlertType, val lat: Double?, val lon: Double?)

data class SmartInput(
    val type: AlertType,
    val reason: String,
    val timeMs: Long,
    val hour: Int,
    val batteryPct: Int,
    val charging: Boolean,
    val network: String,
    val accuracyM: Int?,
    val lat: Double?,
    val lon: Double?,
    val hist: List<Hist>, // earlier alerts, NOT including this one
    val english: Boolean = false,
)

data class Assessment(
    val risk: Risk,
    val summary: String,
    val actions: List<String>,
    val falseTriggerLikely: Boolean,
    val movedMeters: Int?,
) {
    /** Short text for Telegram: risk + first sentence + the top action. */
    fun line(max: Int = 240): String {
        val first = summary.substringBefore(". ").trimEnd('.')
        val act = actions.firstOrNull()?.let { " >> $it" } ?: ""
        val t = "${risk.label.uppercase()}: $first.$act"
        return if (t.length > max) t.take(max - 3).trimEnd() + "..." else t
    }
}

/**
 * The local smart engine: plain rules, no internet, no API key, no account.
 * It never changes anything by itself; it only describes an alert and suggests next steps.
 */
object RiskEngine {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    fun historyOf(records: List<EventRecord>): List<Hist> =
        records.map { Hist(it.timeMs, AlertType.classify(it.reason), it.lat, it.lon) }

    fun assess(i: SmartInput): Assessment {
        val en = i.english
        fun t(hi: String, e: String) = if (en) e else hi
        val past = i.hist.filter { it.timeMs <= i.timeMs }
        val day = past.filter { i.timeMs - it.timeMs <= DAY }
        val hour = past.filter { i.timeMs - it.timeMs <= HOUR }
        val sameDay = day.count { it.type == i.type }
        val recent15 = past.count { i.timeMs - it.timeMs <= 15 * MIN }
        val wrongHour = hour.count { it.type == AlertType.WRONG_UNLOCK } + (if (i.type == AlertType.WRONG_UNLOCK) 1 else 0)
        val simHour = hour.any { it.type == AlertType.SIM } || i.type == AlertType.SIM

        // how far did the phone move since the last alert with a position (within the last hour)?
        val moved = if (i.lat != null && i.lon != null) {
            hour.filter { it.lat != null && it.lon != null }.maxByOrNull { it.timeMs }
                ?.let { distanceM(it.lat!!, it.lon!!, i.lat, i.lon).toInt() }
        } else null
        val movedFar = moved != null && moved > 300

        var score = when (i.type) {
            AlertType.SIM -> 3 // a SIM removed or swapped is always serious
            AlertType.WRONG_UNLOCK -> 1
            AlertType.MANUAL -> if (i.reason.contains("panic", true)) 1 else 0
            else -> 0
        }
        val night = i.hour >= 23 || i.hour < 6
        if (wrongHour >= 3) score++
        if (movedFar) score++
        if (simHour && wrongHour > 0 && i.type != AlertType.MANUAL) score += 2
        if (night && i.type != AlertType.MANUAL && i.type != AlertType.MOTION) score++
        if (i.type == AlertType.MOTION) {
            // a jerk alone is weak evidence; it only matters when something else points to a real theft
            val corroborated = movedFar || wrongHour > 0 || simHour
            if (corroborated) score++
        }
        if (i.type != AlertType.MOTION && recent15 >= 3) score++

        val corroboratedAny = movedFar || wrongHour > 0 || simHour
        val falseLikely = i.type == AlertType.MOTION && sameDay + 1 >= 4 && !corroboratedAny
        val risk = if (falseLikely) Risk.LOW else when {
            score >= 3 -> Risk.HIGH
            score >= 1 -> Risk.MEDIUM
            else -> Risk.LOW
        }

        val s = StringBuilder()
        s.append(i.type.title).append(": ").append(t("${risk.label} khatra. ", "${risk.label} risk. "))
        if (i.type == AlertType.WRONG_UNLOCK) s.append(t("Ghante me $wrongHour galat koshish. ", "$wrongHour wrong attempts this hour. "))
        if (i.type == AlertType.SIM) s.append(t("SIM me badlav mila. ", "SIM change detected. "))
        if (simHour && wrongHour > 0 && i.type != AlertType.SIM) s.append(t("SIM badlav aur galat password dono hue. ", "SIM change and wrong passwords both happened. "))
        if (i.batteryPct in 0..15 && !i.charging) s.append(t("Battery sirf ${i.batteryPct}%. ", "Battery only ${i.batteryPct}%. "))
        if (moved != null && movedFar) s.append(t("Pichhle alert se phone ~$moved m door hai. ", "Phone is ~$moved m from the last alert. "))
        if (i.accuracyM != null) s.append(t("Location sahi ~${i.accuracyM} m tak. ", "Location accurate to ~${i.accuracyM} m. "))
        else if (i.lat == null) s.append(t("Location nahi mili. ", "No location. "))
        if (night && i.type != AlertType.MANUAL) s.append(t("Raat ka samay. ", "Night time. "))
        if (sameDay > 0) s.append(t("24 ghante me ${sameDay + 1} aise alert. ", "${sameDay + 1} such alerts in 24 hours. "))
        if (falseLikely) s.append(t("Galat trigger ka shak: koi aur sanket nahi mila. ", "Likely false trigger: nothing else points to a theft. "))

        val actions = mutableListOf<String>()
        when (risk) {
            Risk.HIGH -> {
                actions.add(t("Turant alert ka Maps link/photo dekhein", "Check the alert's map link/photo now"))
                actions.add(t("Ghar walon se phone ke bare me poochein", "Ask people at home about the phone"))
                actions.add(t("Zaroorat ho to SIM/accounts lock karein", "If needed, lock the SIM and accounts"))
            }
            Risk.MEDIUM -> {
                actions.add(t("Photo aur location check karein", "Check the photo and location"))
                if (night) actions.add(t("Phone paas hai ya nahi dekh lein", "Make sure the phone is with you"))
            }
            Risk.LOW -> {
                if (falseLikely) {
                    actions.add(t("Sensitivity Kam karein ya Quiet use karein", "Lower sensitivity or use Quiet"))
                } else if (i.type == AlertType.MANUAL) {
                    actions.add(t("Test theek raha, koi action nahi", "Test worked, no action needed"))
                } else {
                    actions.add(t("Koi action zaroori nahi", "No action needed"))
                }
            }
        }
        if (i.batteryPct in 0..15 && !i.charging) actions.add(t("Battery kam hai: jaldi dhyan dein", "Battery is low: act soon"))
        if (i.type == AlertType.BATTERY) actions.add(0, t("Phone ka ye aakhri location hai: Maps me dekhein", "This is the phone's last known location: check the map"))
        if (i.lat == null && i.type != AlertType.MANUAL) actions.add(t("Phone ki Location ON rakhein", "Keep phone Location ON"))
        return Assessment(risk, s.toString().trim(), actions, falseLikely, moved)
    }

    fun inputFor(r: EventRecord, earlier: List<EventRecord>, english: Boolean = false): SmartInput {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = r.timeMs }
        return SmartInput(
            type = AlertType.classify(r.reason), reason = r.reason, timeMs = r.timeMs,
            hour = cal.get(java.util.Calendar.HOUR_OF_DAY), batteryPct = r.batteryPct, charging = r.charging,
            network = r.network, accuracyM = r.accuracyM?.toInt(), lat = r.lat, lon = r.lon,
            hist = historyOf(earlier.filter { it.id != r.id }), english = english,
        )
    }
}
