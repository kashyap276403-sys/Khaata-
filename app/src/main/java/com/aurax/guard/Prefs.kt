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
    fun guardOn(c: Context) = bool(c, "guard_on", false)
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
    fun snoozeUntil(c: Context) = p(c).getLong("snooze_until", 0L)
    fun setSnoozeUntil(c: Context, v: Long) = p(c).edit().putLong("snooze_until", v).apply()
    fun snoozed(c: Context) = System.currentTimeMillis() < snoozeUntil(c)

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
