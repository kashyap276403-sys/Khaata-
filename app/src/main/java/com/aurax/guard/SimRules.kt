package com.aurax.guard

/** Pure SIM change logic (no Android classes), so it can be unit tested. */
object SimRules {
    // Same numbers as TelephonyManager.SIM_STATE_*
    private const val UNKNOWN = 0
    private const val ABSENT = 1
    private const val NOT_READY = 6
    private const val IO_ERROR = 8

    /**
     * One comparable text for the SIM slots, or null while Android is still
     * starting up (UNKNOWN / NOT_READY), so a reboot never looks like a change.
     */
    fun fingerprint(states: List<Int>, operator: String): String? {
        if (states.isEmpty()) return null
        if (states.any { it == UNKNOWN || it == NOT_READY || it == IO_ERROR }) return null
        val slots = states.joinToString(",") { if (it == ABSENT) "-" else "sim" }
        return "$slots|${operator.trim()}"
    }

    /** Returns a short description if this is a real change, else null. */
    fun change(previous: String, current: String?): String? {
        if (current == null || previous.isEmpty() || previous == current) return null
        val (pSlots, pOp) = previous.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val (cSlots, cOp) = current.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when {
            pSlots != cSlots && cSlots.split(",").count { it == "sim" } < pSlots.split(",").count { it == "sim" } ->
                "SIM nikali gayi ($pSlots -> $cSlots)"
            pSlots != cSlots -> "SIM lagayi gayi ya slot badla ($pSlots -> $cSlots)"
            else -> "SIM/carrier badla ($pOp -> $cOp)"
        }
    }

    /** Do not alert twice within a minute (Android sends several SIM broadcasts). */
    fun cooledDown(lastAlertMs: Long, nowMs: Long) = nowMs - lastAlertMs >= 60_000
}
