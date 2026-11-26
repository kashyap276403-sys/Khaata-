package com.aurax.guard.smart

import org.json.JSONObject

/** The settings Tactical Lockdown changes, so /normal can put every one of them back. */
data class LockSnap(
    val charger: Boolean, val sim: Boolean, val wrongUnlock: Boolean, val stealth: Boolean, val tracePhoto: Boolean,
    val traceOn: Boolean, val saverAuto: Boolean, val batteryAlert: Boolean, val ignoreCharging: Boolean,
) {
    fun toJson() = JSONObject().put("c", charger).put("s", sim).put("w", wrongUnlock).put("st", stealth).put("tp", tracePhoto)
        .put("to", traceOn).put("sa", saverAuto).put("ba", batteryAlert).put("ic", ignoreCharging).toString()

    companion object {
        fun fromJson(s: String?): LockSnap? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                LockSnap(o.getBoolean("c"), o.getBoolean("s"), o.getBoolean("w"), o.getBoolean("st"), o.getBoolean("tp"), o.getBoolean("to"),
                    o.getBoolean("sa"), o.getBoolean("ba"), o.getBoolean("ic"))
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Tactical Lockdown = arm the tamper guards together. It only turns protections ON (never Thief Protection OFF),
 * adds no GPS polling (Emergency Trace still only runs when the battery is low), and keeps the motion sensitivity
 * untouched so it cannot cause false alarms. Jhatka lock stays ON after /normal on purpose.
 */
object LockdownRules {
    val TARGET = LockSnap(
        charger = true, sim = true, wrongUnlock = true, stealth = true, tracePhoto = true,
        traceOn = true, saverAuto = true, batteryAlert = true, ignoreCharging = false,
    )

    /** What to remember when entering: keep the FIRST snapshot if lockdown is already active (so repeating it cannot lose your real settings). */
    fun snapshotToKeep(active: Boolean, saved: String?, current: LockSnap): String =
        if (active && LockSnap.fromJson(saved) != null) saved!! else current.toJson()

    /** What /normal restores. If the snapshot is lost, keep everything ON (safe side) instead of guessing OFF. */
    fun restoreTarget(saved: String?): LockSnap = LockSnap.fromJson(saved) ?: TARGET.copy(stealth = false, ignoreCharging = false)
}
