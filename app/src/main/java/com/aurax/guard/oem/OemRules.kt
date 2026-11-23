package com.aurax.guard.oem

/** Which phone skin we are on, and what to tell the user. Pure logic, unit tested. */
enum class OemFamily(val title: String) {
    VIVO("vivo / iQOO (OriginOS or Funtouch OS)"),
    MOTOROLA("Motorola (Hello UI)"),
    OTHER("Other Android"),
}

object OemRules {
    fun family(manufacturer: String, brand: String, osName: String = ""): OemFamily {
        val all = "$manufacturer $brand $osName".lowercase()
        return when {
            "vivo" in all || "iqoo" in all || "funtouch" in all || "originos" in all -> OemFamily.VIVO
            "motorola" in all || "moto" in all || "hello ui" in all -> OemFamily.MOTOROLA
            else -> OemFamily.OTHER
        }
    }

    /** Short skin name for display, e.g. "OriginOS 6" or "Funtouch OS 15". */
    fun skinLabel(family: OemFamily, osName: String, osVersion: String): String = when (family) {
        OemFamily.VIVO -> listOf(osName.ifBlank { "Funtouch/OriginOS" }, osVersion).filter { it.isNotBlank() }.joinToString(" ")
        OemFamily.MOTOROLA -> "Hello UI"
        OemFamily.OTHER -> "Android"
    }

    fun steps(family: OemFamily): List<String> = when (family) {
        OemFamily.VIVO -> listOf(
            "Autostart / Background start: auraX Guard ko ALLOW karein.",
            "Battery > Background power consumption (High background power): auraX Guard ko 'Allow' / 'No restriction' karein.",
            "Recent apps me auraX Guard card ko neeche kheech kar LOCK karein.",
            "Notifications me 'Lock screen par dikhayein' chalu rakhein.",
            "Settings > Biometrics & password > Device admin me auraX Guard ON rakhein.",
        )
        OemFamily.MOTOROLA -> listOf(
            "Settings > Apps > auraX Guard > Battery: 'Unrestricted' karein.",
            "Recent apps me auraX Guard ko lock/pin karein (agar option ho).",
            "Notifications me lock screen par dikhana chalu rakhein.",
            "Device admin ON rakhein (Settings > Security).",
        )
        OemFamily.OTHER -> listOf(
            "Battery optimization me auraX Guard ko 'Unrestricted' karein.",
            "Autostart / background permission ho to allow karein.",
        )
    }

    /** Score out of the checks that matter (true = ok). */
    fun summary(checks: List<Pair<String, Boolean>>): String {
        val bad = checks.count { !it.second }
        return if (bad == 0) "Sab theek: ${checks.size}/${checks.size}" else "$bad cheezein theek karni baaki hain (${checks.size - bad}/${checks.size} ok)"
    }
}
