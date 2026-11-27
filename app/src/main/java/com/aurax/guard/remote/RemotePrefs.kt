package com.aurax.guard.remote

import android.content.Context

/** Remote control settings and small state. Master switch is OFF by default. */
object RemotePrefs {
    private fun p(c: Context) = c.getSharedPreferences("remote", Context.MODE_PRIVATE)
    fun masterOn(c: Context) = p(c).getBoolean("master", false)
    fun setMasterOn(c: Context, v: Boolean) {
        // turning ON starts fresh: old chat history must never run as commands
        p(c).edit().putBoolean("master", v).apply { if (v) putLong("offset", 0L).putBoolean("synced", false) }.apply()
    }
    fun locOn(c: Context) = p(c).getBoolean("loc", true)
    fun setLocOn(c: Context, v: Boolean) = p(c).edit().putBoolean("loc", v).apply()
    fun photoOn(c: Context) = p(c).getBoolean("photo", true)
    fun setPhotoOn(c: Context, v: Boolean) = p(c).edit().putBoolean("photo", v).apply()
    fun modeOn(c: Context) = p(c).getBoolean("mode", true)
    fun setModeOn(c: Context, v: Boolean) = p(c).edit().putBoolean("mode", v).apply()

    fun offset(c: Context) = p(c).getLong("offset", 0L)
    fun setOffset(c: Context, v: Long) = p(c).edit().putLong("offset", v).apply()
    fun synced(c: Context) = p(c).getBoolean("synced", false)
    fun setSynced(c: Context, v: Boolean) = p(c).edit().putBoolean("synced", v).apply()
    fun lastPollMs(c: Context) = p(c).getLong("last_poll", 0L)
    fun setLastPollMs(c: Context, v: Long) = p(c).edit().putLong("last_poll", v).apply()
    fun failures(c: Context) = p(c).getInt("fails", 0)
    fun setFailures(c: Context, v: Int) = p(c).edit().putInt("fails", v).apply()
    fun lastRun(c: Context, name: String) = p(c).getLong("run_$name", 0L)
    fun setLastRun(c: Context, name: String, v: Long) = p(c).edit().putLong("run_$name", v).apply()
    fun lastNote(c: Context) = p(c).getString("note", "") ?: ""
    fun setLastNote(c: Context, v: String) = p(c).edit().putString("note", v).apply()
    fun retryAt(c: Context) = p(c).getLong("retry_at", 0L)
    fun retryCmd(c: Context) = p(c).getString("retry_cmd", "") ?: ""
    fun setRetry(c: Context, at: Long, cmd: String) = p(c).edit().putLong("retry_at", at).putString("retry_cmd", cmd).apply()
    fun lastDenyMs(c: Context) = p(c).getLong("deny", 0L)
    fun setLastDenyMs(c: Context, v: Long) = p(c).edit().putLong("deny", v).apply()
    fun lastStrangerMs(c: Context) = p(c).getLong("stranger", 0L)
    fun setLastStrangerMs(c: Context, v: Long) = p(c).edit().putLong("stranger", v).apply()

    /** Runs in the last hour (stored as a short list of timestamps). */
    fun runsLastHour(c: Context, now: Long): Int = times(c).count { now - it < 3_600_000L }
    fun addRun(c: Context, now: Long) {
        val keep = (times(c).filter { now - it < 3_600_000L } + now).takeLast(40)
        p(c).edit().putString("runs", keep.joinToString(",")).apply()
    }
    private fun times(c: Context): List<Long> = (p(c).getString("runs", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
}
