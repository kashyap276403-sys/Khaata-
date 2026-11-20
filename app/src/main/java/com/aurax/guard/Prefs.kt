package com.aurax.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small settings + event log, stored on the phone only. */
object Prefs {
    private const val FILE = "guard"

    // Snatch sensitivity: index -> movement strength (m/s^2, gravity removed).
    val snatchThresholds = floatArrayOf(14f, 20f, 28f)
    val snatchNames = arrayOf("Zyada", "Madhyam", "Kam")

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun selfieOn(c: Context) = p(c).getBoolean("selfie_on", true)
    fun setSelfieOn(c: Context, v: Boolean) = p(c).edit().putBoolean("selfie_on", v).apply()

    /** Take a photo once this many wrong attempts happened in a row. */
    fun attemptsBeforePhoto(c: Context) = p(c).getInt("attempts", 1)
    fun setAttemptsBeforePhoto(c: Context, v: Int) = p(c).edit().putInt("attempts", v).apply()

    fun snatchOn(c: Context) = p(c).getBoolean("snatch_on", false)
    fun setSnatchOn(c: Context, v: Boolean) = p(c).edit().putBoolean("snatch_on", v).apply()

    fun sensitivity(c: Context) = p(c).getInt("sens", 1).coerceIn(0, snatchThresholds.size - 1)
    fun setSensitivity(c: Context, v: Int) = p(c).edit().putInt("sens", v).apply()

    fun failures(c: Context) = p(c).getInt("failures", 0)
    fun bumpFailures(c: Context): Int {
        val n = failures(c) + 1
        p(c).edit().putInt("failures", n).apply()
        return n
    }
    fun resetFailures(c: Context) = p(c).edit().putInt("failures", 0).apply()

    fun photoCount(c: Context) = p(c).getInt("photos", 0)
    fun addPhoto(c: Context) = p(c).edit().putInt("photos", photoCount(c) + 1).apply()

    /** Last thing that happened (shown on the main screen so problems are visible). */
    fun lastEvent(c: Context) = p(c).getString("last_event", "Abhi tak kuch nahi hua") ?: ""
    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        p(c).edit().putString("last_event", "$t  $msg").apply()
    }
}
