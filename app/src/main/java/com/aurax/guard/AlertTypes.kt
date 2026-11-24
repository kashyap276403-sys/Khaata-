package com.aurax.guard

/** Groups alert reasons into types so each type can be switched on/off and filtered. Pure logic. */
enum class AlertType(val key: String, val title: String) {
    MOTION("motion", "Jhatka / motion"),
    CHARGER("charger", "Charger nikala"),
    WRONG_UNLOCK("wrong", "Galat password / PIN"),
    SIM("sim", "SIM badli"),
    BATTERY("battery", "Battery bahut kam"),
    TRACE("trace", "Emergency trace"),
    DIGEST("digest", "Digest / heartbeat"),
    MANUAL("manual", "Test / Panic"),
    OTHER("other", "Anya");

    companion object {
        fun classify(reason: String): AlertType {
            val r = reason.lowercase()
            return when {
                r.startsWith("sim") -> SIM
                r.startsWith("battery") -> BATTERY
                r.startsWith("emergency") || r.startsWith("guard boost") -> TRACE
                r.startsWith("daily digest") || r.startsWith("heartbeat") -> DIGEST
                "jhatka" in r || "haath lagaya" in r -> MOTION
                "charger" in r -> CHARGER
                "galat" in r -> WRONG_UNLOCK
                "test" in r || "panic" in r -> MANUAL
                else -> OTHER
            }
        }

        /** Test and Panic always record, so you can always check that things work. */
        fun allowed(c: android.content.Context, reason: String): Boolean {
            val t = classify(reason)
            return when (t) {
                MANUAL, OTHER, TRACE, DIGEST -> true
                SIM -> Prefs.simAlertOn(c)
                BATTERY -> Prefs.batteryAlertOn(c)
                else -> Prefs.alertTypeOn(c, t.key)
            }
        }
    }
}
