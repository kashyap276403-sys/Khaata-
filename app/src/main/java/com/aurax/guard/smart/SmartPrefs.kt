package com.aurax.guard.smart

import android.content.Context

/** Settings and small state for the emergency / smart features. Local to this phone. All OFF by default. */
object SmartPrefs {
    private fun p(c: Context) = c.getSharedPreferences("smart2", Context.MODE_PRIVATE)
    private fun b(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    private fun sb(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    // ---- emergency trace ----
    fun traceOn(c: Context) = b(c, "trace_on", false)
    fun setTraceOn(c: Context, v: Boolean) = sb(c, "trace_on", v)
    fun threshold(c: Context) = p(c).getInt("trace_thr", 40).coerceIn(10, 80)
    fun setThreshold(c: Context, v: Int) = p(c).edit().putInt("trace_thr", v.coerceIn(10, 80)).apply()
    fun intervalMin(c: Context) = p(c).getInt("trace_int", 60).coerceIn(5, 480)
    fun setIntervalMin(c: Context, v: Int) = p(c).edit().putInt("trace_int", v.coerceIn(5, 480)).apply()
    fun criticalPct(c: Context) = p(c).getInt("trace_crit", 15).coerceIn(3, 30)
    fun setCriticalPct(c: Context, v: Int) = p(c).edit().putInt("trace_crit", v.coerceIn(3, 30)).apply()
    fun tracePhoto(c: Context) = b(c, "trace_photo", true)
    fun setTracePhoto(c: Context, v: Boolean) = sb(c, "trace_photo", v)

    fun cfg(c: Context) = TraceCfg(traceOn(c), threshold(c), intervalMin(c), criticalPct(c))
    fun state(c: Context) = TraceState(b(c, "t_active", false), p(c).getLong("t_last", 0L), b(c, "t_crit", false))
    fun setState(c: Context, s: TraceState) = p(c).edit().putBoolean("t_active", s.active).putLong("t_last", s.lastTraceMs).putBoolean("t_crit", s.criticalSent).apply()

    // ---- guard boost ----
    fun boostOn(c: Context) = b(c, "boost_on", false)
    fun setBoostOn(c: Context, v: Boolean) = sb(c, "boost_on", v)
    fun boostUntil(c: Context) = p(c).getLong("boost_until", 0L)
    fun boostLast(c: Context) = p(c).getLong("boost_last", 0L)
    fun boostCount(c: Context) = p(c).getInt("boost_n", 0)
    fun setBoost(c: Context, until: Long, last: Long, n: Int) = p(c).edit().putLong("boost_until", until).putLong("boost_last", last).putInt("boost_n", n).apply()

    // ---- digest / heartbeat ----
    fun digestOn(c: Context) = b(c, "digest_on", false)
    fun setDigestOn(c: Context, v: Boolean) = sb(c, "digest_on", v)
    fun digestMin(c: Context) = p(c).getInt("digest_min", 21 * 60)
    fun setDigestMin(c: Context, v: Int) = p(c).edit().putInt("digest_min", v).apply()
    fun digestLastDay(c: Context) = p(c).getInt("digest_day", -1)
    fun setDigestLastDay(c: Context, v: Int) = p(c).edit().putInt("digest_day", v).apply()
    fun heartbeatOn(c: Context) = b(c, "hb_on", false)
    fun setHeartbeatOn(c: Context, v: Boolean) = sb(c, "hb_on", v)
    fun heartbeatHours(c: Context) = p(c).getInt("hb_h", 12).coerceIn(1, 48)
    fun setHeartbeatHours(c: Context, v: Int) = p(c).edit().putInt("hb_h", v.coerceIn(1, 48)).apply()
    fun heartbeatLast(c: Context) = p(c).getLong("hb_last", 0L)
    fun setHeartbeatLast(c: Context, v: Long) = p(c).edit().putLong("hb_last", v).apply()

    // ---- tactical lockdown ----
    fun lockdownOn(c: Context) = b(c, "lockdown", false)
    fun setLockdownOn(c: Context, v: Boolean) = sb(c, "lockdown", v)
    fun lockdownSaved(c: Context) = p(c).getString("lockdown_saved", "") ?: ""
    fun setLockdownSaved(c: Context, v: String) = p(c).edit().putString("lockdown_saved", v).apply()

    // ---- APK / file check ----
    fun apkScanOn(c: Context) = b(c, "apk_on", true)
    fun setApkScanOn(c: Context, v: Boolean) = sb(c, "apk_on", v)

    // ---- battery saver ----
    fun saverManual(c: Context) = b(c, "saver_on", false)
    fun setSaverManual(c: Context, v: Boolean) = sb(c, "saver_on", v)
    fun saverAuto(c: Context) = b(c, "saver_auto", true)
    fun setSaverAuto(c: Context, v: Boolean) = sb(c, "saver_auto", v)
    fun saverPct(c: Context) = p(c).getInt("saver_pct", 20).coerceIn(5, 40)
    fun setSaverPct(c: Context, v: Int) = p(c).edit().putInt("saver_pct", v.coerceIn(5, 40)).apply()

    // ---- guard behaviour ----
    fun stealth(c: Context) = b(c, "stealth", false)
    fun setStealth(c: Context, v: Boolean) = sb(c, "stealth", v)
    fun ignoreCharging(c: Context) = b(c, "ign_chg", false)
    fun setIgnoreCharging(c: Context, v: Boolean) = sb(c, "ign_chg", v)
    fun mode(c: Context) = p(c).getString("mode", "") ?: ""
    fun setMode(c: Context, v: String) = p(c).edit().putString("mode", v).apply()
}
