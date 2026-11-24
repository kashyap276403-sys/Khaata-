package com.aurax.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small settings + event history, stored on the phone only. */
object Prefs {
    private const val FILE = "guard"
    private const val MAX_EVENTS = 25

    // Snatch sensitivity: index -> movement strength (m/s^2, gravity removed).
    val snatchThresholds = floatArrayOf(14f, 20f, 28f)
    val snatchNames = arrayOf("Zyada", "Madhyam", "Kam")

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun bool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    private fun setBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    /** Master switch: the always-on guard service. */
    fun guardOn(c: Context) = bool(c, "guard_on", true)
    fun setGuardOn(c: Context, v: Boolean) = setBool(c, "guard_on", v)

    fun selfieOn(c: Context) = bool(c, "selfie_on", true)
    fun setSelfieOn(c: Context, v: Boolean) = setBool(c, "selfie_on", v)

    /** Take a photo once this many wrong attempts happened in a row. */
    fun attemptsBeforePhoto(c: Context) = p(c).getInt("attempts", 1).coerceIn(1, 3)
    fun setAttemptsBeforePhoto(c: Context, v: Int) = p(c).edit().putInt("attempts", v).apply()

    fun snatchLockOn(c: Context) = bool(c, "snatch_lock", true)
    fun setSnatchLockOn(c: Context, v: Boolean) = setBool(c, "snatch_lock", v)

    fun chargerAlarmOn(c: Context) = bool(c, "charger_alarm", false)
    fun setChargerAlarmOn(c: Context, v: Boolean) = setBool(c, "charger_alarm", v)

    fun sirenOn(c: Context) = bool(c, "siren", true)
    fun setSirenOn(c: Context, v: Boolean) = setBool(c, "siren", v)

    fun sensitivity(c: Context) = p(c).getInt("sens", 1).coerceIn(0, snatchThresholds.size - 1)
    fun setSensitivity(c: Context, v: Int) = p(c).edit().putInt("sens", v).apply()

    /** Detection is paused until this time (wall clock millis), 0 = not paused. */
    /** Ignore motion while the phone is unlocked and being used (fewer false locks). On by default. */
    fun ignoreWhileUnlocked(c: Context) = bool(c, "ign_unlocked", true)
    fun setIgnoreWhileUnlocked(c: Context, v: Boolean) = setBool(c, "ign_unlocked", v)

    /** Short audio clip with each trigger. OFF by default: it is a microphone recording. */
    fun audioOn(c: Context) = bool(c, "audio_on", false)
    fun setAudioOn(c: Context, v: Boolean) = setBool(c, "audio_on", v)
    fun audioSeconds(c: Context) = p(c).getInt("audio_sec", 12).coerceIn(10, 15)
    fun setAudioSeconds(c: Context, v: Int) = p(c).edit().putInt("audio_sec", v.coerceIn(10, 15)).apply()

    /** Alert when the SIM is removed or changed. ON by default (only sends if Telegram is set up). */
    fun simAlertOn(c: Context) = bool(c, "sim_alert", true)
    fun setSimAlertOn(c: Context, v: Boolean) = setBool(c, "sim_alert", v)
    fun simFingerprint(c: Context) = p(c).getString("sim_fp", "") ?: ""
    fun setSimFingerprint(c: Context, v: String) = p(c).edit().putString("sim_fp", v).apply()
    fun simLastAlertMs(c: Context) = p(c).getLong("sim_last", 0L)
    fun setSimLastAlertMs(c: Context, v: Long) = p(c).edit().putLong("sim_last", v).apply()

    /** After the screen turns on/off or the phone is unlocked, ignore motion for this many seconds (placing the phone down, picking it up). */
    fun graceSec(c: Context) = p(c).getInt("grace_sec", 10).coerceIn(3, 30)
    fun setGraceSec(c: Context, v: Int) = p(c).edit().putInt("grace_sec", v.coerceIn(3, 30)).apply()

    /** Per-type switch for recording/sending an alert (queue + Telegram). Detection itself has its own switches. */
    fun alertTypeOn(c: Context, type: String) = bool(c, "al_$type", true)
    fun setAlertTypeOn(c: Context, type: String, v: Boolean) = setBool(c, "al_$type", v)

    /** Local Smart Engine (rules on the phone, no internet/API). ON by default. */
    fun smartOn(c: Context) = bool(c, "smart_on", true)
    fun setSmartOn(c: Context, v: Boolean) = setBool(c, "smart_on", v)

    /** Let the smart engine make the motion guard gentler by itself when alerts look accidental. OFF by default. */
    fun autoTune(c: Context) = bool(c, "auto_tune", false)
    fun setAutoTune(c: Context, v: Boolean) = setBool(c, "auto_tune", v)
    fun autoTuneLastMs(c: Context) = p(c).getLong("auto_last", 0L)
    fun setAutoTuneLastMs(c: Context, v: Long) = p(c).edit().putLong("auto_last", v).apply()
    fun autoNote(c: Context) = p(c).getString("auto_note", "") ?: ""
    fun setAutoNote(c: Context, v: String) = p(c).edit().putString("auto_note", v).apply()
    fun setAutoPrev(c: Context, level: Int, grace: Int) = p(c).edit().putInt("auto_prev_l", level).putInt("auto_prev_g", grace).apply()
    fun autoPrev(c: Context): Pair<Int, Int>? = if (p(c).contains("auto_prev_l")) p(c).getInt("auto_prev_l", 1) to p(c).getInt("auto_prev_g", 10) else null
    fun clearAutoPrev(c: Context) = p(c).edit().remove("auto_prev_l").remove("auto_prev_g").apply()

    fun snoozeUntil(c: Context) = p(c).getLong("snooze_until", 0L)
    fun setSnoozeUntil(c: Context, v: Long) = p(c).edit().putLong("snooze_until", v).apply()
    fun snoozed(c: Context): Boolean = System.currentTimeMillis() < snoozeUntil(c) || scheduledQuiet(c)

    // ---- daily quiet schedule ----
    fun quietSchedOn(c: Context) = bool(c, "qs_on", false)
    fun setQuietSchedOn(c: Context, v: Boolean) { setBool(c, "qs_on", v); schedStamp = 0 }
    fun quietFromMin(c: Context) = p(c).getInt("qs_from", 7 * 60)
    fun quietToMin(c: Context) = p(c).getInt("qs_to", 9 * 60)
    fun setQuietWindow(c: Context, from: Int, to: Int) { p(c).edit().putInt("qs_from", from).putInt("qs_to", to).apply(); schedStamp = 0 }

    @Volatile private var schedStamp = 0L
    @Volatile private var schedResult = false

    /** Inside the daily quiet window? Re-checked at most every 20 s, because the motion sensor asks 50 times a second. */
    fun scheduledQuiet(c: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - schedStamp < 20_000) return schedResult
        schedResult = try {
            if (!quietSchedOn(c)) false else {
                val cal = java.util.Calendar.getInstance()
                QuietRules.inWindow(cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE), quietFromMin(c), quietToMin(c))
            }
        } catch (_: Exception) {
            false
        }
        schedStamp = now
        return schedResult
    }

    /** Low-battery "last location" alert. OFF by default (Android's low-battery warning fires on normal days too). */
    fun batteryAlertOn(c: Context) = bool(c, "al_battery", false)
    fun setBatteryAlertOn(c: Context, v: Boolean) = setBool(c, "al_battery", v)
    fun batteryLastMs(c: Context) = p(c).getLong("bat_last", 0L)
    fun setBatteryLastMs(c: Context, v: Long) = p(c).edit().putLong("bat_last", v).apply()

    /** Phone lying still: any touch/movement sets off the siren. */
    fun parkOn(c: Context) = bool(c, "park", false)
    fun setParkOn(c: Context, v: Boolean) = setBool(c, "park", v)

    /** Privacy: hide the app from screenshots and the recent-apps list. */
    fun secureScreen(c: Context) = bool(c, "secure", true)
    fun setSecureScreen(c: Context, v: Boolean) = setBool(c, "secure", v)

    /** Privacy: false = intruder photos stay inside the app, not in the gallery. */
    fun photosInGallery(c: Context) = bool(c, "photos_gallery", true)
    fun setPhotosInGallery(c: Context, v: Boolean) = setBool(c, "photos_gallery", v)

    /** App PIN (stored only as a salted PBKDF2 hash). */
    fun pinHash(c: Context): String? = p(c).getString("pin_hash", null)
    fun pinSalt(c: Context): String? = p(c).getString("pin_salt", null)
    fun hasPin(c: Context) = pinHash(c) != null && pinSalt(c) != null
    fun setPin(c: Context, salt: String, hash: String) =
        p(c).edit().putString("pin_salt", salt).putString("pin_hash", hash).apply()
    fun clearPin(c: Context) = p(c).edit().remove("pin_salt").remove("pin_hash").apply()

    /** Delete intruder photos older than this many days (0 = keep until deleted by hand). */
    fun retentionDays(c: Context) = p(c).getInt("retention_days", 0)
    fun setRetentionDays(c: Context, v: Int) = p(c).edit().putInt("retention_days", v).apply()

    /** Minutes the app stays unlocked after the PIN was entered. */
    fun lockGraceMin(c: Context) = p(c).getInt("lock_grace_min", 5)
    fun setLockGraceMin(c: Context, v: Int) = p(c).edit().putInt("lock_grace_min", v).apply()

    /** Open the app with a fingerprint as well as the PIN. */
    fun bioOn(c: Context) = bool(c, "bio", false)
    fun setBioOn(c: Context, v: Boolean) = setBool(c, "bio", v)

    /** Torch strobe + vibration while the siren sounds. */
    fun sirenFlash(c: Context) = bool(c, "siren_flash", true)
    fun setSirenFlash(c: Context, v: Boolean) = setBool(c, "siren_flash", v)

    /** True once the first-run permission request was shown. */
    fun permsAsked(c: Context) = bool(c, "perms_asked", false)
    fun setPermsAsked(c: Context) = setBool(c, "perms_asked", true)

    fun clearEvents(c: Context) = p(c).edit().remove("events").apply()

    fun failures(c: Context) = p(c).getInt("failures", 0)
    fun bumpFailures(c: Context): Int {
        val n = failures(c) + 1
        p(c).edit().putInt("failures", n).apply()
        return n
    }
    fun resetFailures(c: Context) = p(c).edit().putInt("failures", 0).apply()

    /** Newest first. */
    fun events(c: Context): List<String> =
        (p(c).getString("events", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun lastEvent(c: Context): String = events(c).firstOrNull() ?: "Abhi tak kuch nahi hua"

    @Synchronized
    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        val list = (listOf("$t  $msg") + events(c)).take(MAX_EVENTS)
        p(c).edit().putString("events", list.joinToString("\n")).apply()
    }
}
