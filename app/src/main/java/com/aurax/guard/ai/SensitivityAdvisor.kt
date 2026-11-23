package com.aurax.guard.ai

import com.aurax.guard.EventRecord

/**
 * Plain, local tuning hints from your own alert history. Works with AI off.
 * It only suggests; nothing is changed automatically.
 */
object SensitivityAdvisor {
    private const val DAY = 24L * 3600 * 1000

    fun isMotion(r: EventRecord) = r.reason.contains("Jhatka", true) || r.reason.contains("haath lagaya", true)

    fun hints(events: List<EventRecord>, nowMs: Long, level: Int, parkOn: Boolean, snatchOn: Boolean): List<String> {
        val out = mutableListOf<String>()
        val motion = events.filter { isMotion(it) }
        val last24 = motion.count { nowMs - it.timeMs in 0..DAY }
        val last7 = motion.count { nowMs - it.timeMs in 0..7 * DAY }
        if (!snatchOn && !parkOn) out.add("Jhatka detection band hai. Phone chheenne par lock nahi hoga.")
        if (last24 >= 4 && level < 2) {
            out.add("Pichhle 24 ghante me $last24 motion alerts aaye. Sensitivity '${if (level == 0) "Madhyam" else "Kam"}' karke dekhein, shayad galat alerts hain.")
        }
        if (last24 >= 4 && level == 2) {
            out.add("Pichhle 24 ghante me $last24 motion alerts aaye, sensitivity pehle se Kam hai. Agar khud uthane par bhi aate hain to 'Phone unlock/use me ho to ignore' ON rakhein.")
        }
        if (parkOn && last24 >= 2) out.add("'Haath mat lagao' mode me $last24 alerts: phone kisi hilti jagah (bag, gaadi) me to nahi rakha?")
        if (last7 == 0 && level == 2 && (snatchOn || parkOn)) out.add("7 din me koi motion alert nahi. Agar asli jhatke par lock na ho to sensitivity 'Madhyam' try karein.")
        if (out.isEmpty()) out.add("Sensitivity theek lag rahi hai (7 din me $last7 motion alerts).")
        return out
    }
}
